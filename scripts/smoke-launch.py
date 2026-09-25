#!/usr/bin/env python3
r"""冒烟启动：把每个工程的客户端真启动一遍，自动分类「起来了」还是「崩了」。

为什么需要它：混入的注入点与访问器目标没有任何编译期校验（本仓库用的注解处理器在这个
版本线上不生成 refmap，等于不校验）。把一个目标早已不存在的过时混入注册进去，
`clean compileJava` 照样 BUILD SUCCESSFUL，只有真启动才会崩。所以「能不能编译」不能作为
「客户端能不能跑」的证据，必须实际启动。

判据（分两段，别只看第二段）：
  * `Sound engine started`（资源重载阶段）**只是门槛**，不是成功信号。
    混入注入是在目标类被加载时才应用的，可能晚于这一行；一见它就杀进程会**假通过**
    ——1.21.6 与 1.21.7 的 `PlayerMixin` 逐字节相同，却只有后者崩，前者只是还没走到。
  * 越过门槛后，进程必须**存活 `--settle` 秒且日志无致命行**才判 `LAUNCHED`
    （主菜单是空闲的，这段时间足够走完资源重载并稳定在标题界面）。
  * 进程在此之前退出 / 出现致命行      → CRASHED，抓出致命行
  * 到超时仍未越过门槛且进程还活着     → TIMEOUT

  注意：这是**主菜单级**判据。只在进世界时才加载的类（如 `Player`）上面注入失败它仍查不出，
  那种覆盖需要 `--quickPlaySingleplayer` 进世界（见 scripts/run-version-tests.ps1）。

用法：
    python scripts/smoke-launch.py 26.2                 # 所有 26.2 的工程
    python scripts/smoke-launch.py --all                # 全部工程（很久）
    python scripts/smoke-launch.py 26.1.2 --loaders fabric neoforge
    python scripts/smoke-launch.py 26.2 --timeout 240 --settle 25

全量扫描要跑很久（67 个工程 × 1~5 分钟），中断后用结果 TSV 续跑：

    python scripts/smoke-launch.py --all --results _smoke/results.tsv --skip-done

日志写到 <仓库>/_smoke/<工程>.log（同版本同加载器的上一次日志会被覆盖，注意留存），
结果 TSV 每判完一个工程就 append 一行：`工程 <TAB> 版本 <TAB> 判定 <TAB> 秒 <TAB> 详情`。
"""
import argparse
import os
import re
import subprocess
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOGDIR = os.path.join(ROOT, '_smoke')
INIT = os.path.join(ROOT, 'gradle', 'init-mirrors.gradle')

GATE_MARK = 'Sound engine started'
FATAL_PATTERNS = [
    re.compile(r'Mixin apply failed'),
    re.compile(r'InvalidAccessorException'),
    re.compile(r'InvalidInjectionException'),
    re.compile(r'MixinInitialisationError'),
    re.compile(r'Failed to download'),
    re.compile(r'Exception in thread "main"'),
    re.compile(r'\[.*/FATAL\]'),
]


JDK_ENV = {17: 'WURSTBPLUS_JAVA17', 21: 'WURSTBPLUS_JAVA21',
           25: 'WURSTBPLUS_JAVA25'}
JDK_FALLBACK = {
    17: [r'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot',
         r'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot',
         r'C:\Program Files\Java\jdk-17'],
    21: [r'C:\Program Files\Java\jdk-21',
         r'C:\Program Files\Java\jdk-21.0.11'],
    25: [r'C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot',
         r'C:\Program Files\Java\jdk-25.0.4'],
}


def jdk_home(version):
    """按仓库约定选 JDK：1.20.x → 17，1.21.x → 21，26.x → 25。

    这不是可有可无的：老工程的 Gradle 8.x 跑在 JDK 25 上会直接死在
    `Unsupported class file major version 69`，与模组本身无关。
    """
    if version.startswith('1.20'):
        want = 17
    elif version.startswith('1.21'):
        want = 21
    else:
        want = 25
    env = os.environ.get(JDK_ENV[want])
    if env and os.path.isdir(env):
        return want, env
    for cand in JDK_FALLBACK[want]:
        if os.path.isdir(cand):
            return want, cand
    return want, None


def projects(version=None, loaders=None):
    out = []
    for dirpath, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in ('build', '.gradle', '_smoke')]
        if 'gradle.properties' not in files:
            continue
        rel = os.path.relpath(dirpath, ROOT).replace(os.sep, '/')
        if rel.startswith('_tools') or rel.startswith('baritone-maven'):
            continue
        try:
            text = open(os.path.join(dirpath, 'gradle.properties'),
                        encoding='utf-8', errors='replace').read()
        except OSError:
            continue
        m = re.search(r'^minecraft_version\s*=\s*(\S+)', text, re.M)
        if not m:
            continue
        if version and m.group(1) != version:
            continue
        if loaders:
            loader = 'forge'
            if rel.startswith('fabric'):
                loader = 'fabric'
            elif rel.startswith('neoforge'):
                loader = 'neoforge'
            if loader not in loaders:
                continue
        out.append((rel, m.group(1)))
    return sorted(out, key=lambda x: (x[1], x[0]))


def kill_tree(proc):
    if proc.poll() is not None:
        return
    # 不要 text=True：Windows 下 taskkill 的输出是本地代码页（GBK），按 UTF-8 解码会抛
    # UnicodeDecodeError，而它发生在 subprocess 的读取线程里，会连带把整个 harness 干掉。
    subprocess.run(['taskkill', '/F', '/T', '/PID', str(proc.pid)],
                   capture_output=True)
    try:
        proc.wait(timeout=30)
    except subprocess.TimeoutExpired:
        proc.kill()


def smoke(proj, version, timeout, settle):
    path = os.path.join(ROOT, proj)
    log = os.path.join(LOGDIR, proj.replace('/', '__') + '.log')
    env = dict(os.environ)
    env['GRADLE_OPTS'] = env.get('GRADLE_OPTS', '')
    want, home = jdk_home(version)
    if home:
        env['JAVA_HOME'] = home
    args = ['./gradlew' if os.path.exists(os.path.join(path, 'gradlew'))
            else 'gradlew.bat', 'runClient', '--init-script', INIT,
            '--console=plain']
    if os.name == 'nt':
        args[0] = os.path.join(path, 'gradlew.bat')
    with open(log, 'w', encoding='utf-8', errors='replace') as fh:
        proc = subprocess.Popen(args, cwd=path, stdout=fh,
                                stderr=subprocess.STDOUT, env=env,
                                creationflags=getattr(
                                    subprocess, 'CREATE_NEW_PROCESS_GROUP', 0))
        started = time.time()
        verdict = None
        gate_at = None
        while True:
            if proc.poll() is not None:
                verdict = 'CRASHED' if proc.returncode != 0 else 'EXITED'
                break
            try:
                text = open(log, encoding='utf-8', errors='replace').read()
            except OSError:
                text = ''
            # NeoForge 在混入注入失败时只打 FATAL 日志，进程不一定退出（实测 1.21.10
            # 就会一直挂着）。致命行优先级最高，先于门槛与存活判断。
            if any(p.search(text) for p in FATAL_PATTERNS):
                verdict = 'CRASHED'
                kill_tree(proc)
                break
            # 门槛：越过资源重载。从这里开始才计存活时间。
            if gate_at is None and GATE_MARK in text:
                gate_at = time.time()
            # 越过门槛后还要活满 settle 秒，才算真的到了主菜单而不是「还没崩」。
            if gate_at is not None and time.time() - gate_at >= settle:
                verdict = 'LAUNCHED'
                kill_tree(proc)
                break
            if time.time() - started > timeout:
                verdict = 'TIMEOUT'
                kill_tree(proc)
                break
            time.sleep(2)
        elapsed = time.time() - started

    text = open(log, encoding='utf-8', errors='replace').read()
    detail = ''
    if verdict in ('CRASHED', 'TIMEOUT'):
        for line in text.splitlines():
            if any(p.search(line) for p in FATAL_PATTERNS):
                detail = line.strip()[:150]
                break
        if not detail:
            tail = [l for l in text.splitlines()
                    if l.strip() and 'Ignoring duplicate' not in l]
            detail = tail[-1][:150] if tail else '(无输出)'
    return verdict, elapsed, detail, log


def load_done(path):
    """读回已判定的工程（结果 TSV 的续跑用）。

    全量 67 个工程要跑很久，中途被打断是常态，所以结果逐行落盘、可续跑：
    只把上一次判成 LAUNCHED 的算「已完成」，失败项会被重跑。
    """
    done = {}
    if not path or not os.path.isfile(path):
        return done
    for line in open(path, encoding='utf-8', errors='replace'):
        parts = line.rstrip('\n').split('\t')
        if len(parts) >= 3:
            done[parts[0]] = parts[2]
    return done


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('version', nargs='?')
    ap.add_argument('--all', action='store_true')
    ap.add_argument('--loaders', nargs='*')
    ap.add_argument('--timeout', type=int, default=420)
    ap.add_argument('--settle', type=int, default=25,
                    help='越过资源重载后需存活的秒数（默认 25）')
    ap.add_argument('--results',
                    help='结果 TSV 追加写到这里，便于中断后 --skip-done 续跑')
    ap.add_argument('--skip-done', action='store_true',
                    help='跳过 --results 里已判成 LAUNCHED 的工程')
    args = ap.parse_args()

    os.makedirs(LOGDIR, exist_ok=True)
    targets = projects(None if args.all else args.version, args.loaders)
    if not targets:
        raise SystemExit('没有匹配的工程')

    done = load_done(args.results) if args.skip_done else {}
    skipped = [t for t in targets if done.get(t[0]) == 'LAUNCHED']
    targets = [t for t in targets if done.get(t[0]) != 'LAUNCHED']

    print('共 %d 个工程，单个超时 %d 秒，越过门槛后需存活 %d 秒'
          % (len(targets), args.timeout, args.settle)
          + ('（已跳过 %d 个 LAUNCHED）' % len(skipped) if skipped else '')
          + '\n')
    results = [(p, v, 'LAUNCHED', 0, '(上次结果)') for p, v in skipped]
    fh = open(args.results, 'a', encoding='utf-8') if args.results else None
    for proj, version in targets:
        verdict, elapsed, detail, log = smoke(proj, version, args.timeout,
                                              args.settle)
        results.append((proj, version, verdict, elapsed, detail))
        print('%-8s %-34s %5.0fs  %s' % (verdict, proj, elapsed, detail))
        if fh:
            fh.write('%s\t%s\t%s\t%.0f\t%s\n'
                     % (proj, version, verdict, elapsed,
                        detail.replace('\t', ' ')))
            fh.flush()
    if fh:
        fh.close()

    print('\n' + '=' * 100)
    bad = [r for r in results if r[2] not in ('LAUNCHED',)]
    print('启动成功 %d / %d；失败 %d'
          % (len(results) - len(bad), len(results), len(bad)))
    for proj, version, verdict, elapsed, detail in bad:
        print('  %-8s %-34s %s' % (verdict, proj, detail))
    print('\n完整日志: %s' % LOGDIR)


if __name__ == '__main__':
    main()
