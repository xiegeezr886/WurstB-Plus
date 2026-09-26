#!/usr/bin/env python3
r"""重建 v1.5 发布矩阵：63 个工程 → build/release-v1.5/。

为什么需要它：`docs/RELEASE.md` 记录的权威脚本 build-v1.5-release.py 在仓库外
（D:\WurstB\tmp-recon\，不随仓库分发），已随那台机器丢失。本脚本按 RELEASE.md 的
规则重建同一套产物，规则如下：

  * 矩阵 = `versions/*`（Forge，除 1.20.1 与 26.3）+ `fabric/versions/*`（除 26.3）
    + `neoforge/versions/*`（除 26.3）+ 两个 1.20.1 根工程 `fabric/`、`neoforge/`。
    根目录 Forge 1.20.1 是 v1.6.0 形态，不在 v1.5 重建范围内（Release 保留其旧产物）。
  * 生产任务：Forge ≤1.21.1 → `jarJar`，≥1.21.3 → `allJar`；Fabric / NeoForge → `build`。
  * JDK：MC ≤1.21.11 → JDK 21；26.x → JDK 25（与 scripts/smoke-launch.py 同一套判定）。
  * 产物命名：`WurstB+.Plus-<mod_version 去掉 v>-<Loader>-<mc>.jar`（Release 上的统一命名）。

用法：
    python scripts/build-v1.5-release.py --plan          # 只打印计划，不构建
    python scripts/build-v1.5-release.py                 # 构建全部
    python scripts/build-v1.5-release.py Forge-1.21.5    # 只构建指定工程
"""
import argparse
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'build', 'release-v1.5')
LOGS = os.path.join(ROOT, 'build', 'release-v1.5-logs')
INIT = os.path.join(ROOT, 'gradle', 'init-mirrors.gradle')

# 与 smoke-launch.py 保持一致：不要把 JDK 路径写死到只有某台机器能跑
JDK_FALLBACK = {
    17: [r'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot',
         r'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot',
         r'C:\Program Files\Java\jdk-17'],
    21: [r'C:\Program Files\Java\jdk-21',
         r'C:\Program Files\Java\jdk-21.0.11'],
    25: [r'C:\Program Files\Microsoft\jdk-25.0.4.101-hotspot',
         r'C:\Program Files\Java\jdk-25.0.4'],
}
JDK_ENV = {17: 'WURSTBPLUS_JAVA17', 21: 'WURSTBPLUS_JAVA21',
           25: 'WURSTBPLUS_JAVA25'}

SKIP_MC = set()             # 26.3 也一并构建：它是 Release v1.5.0 上尚未发布的版本
ROOT_FORGE_SKIP = True      # 根 Forge 1.20.1 是 v1.6.0 形态，保留旧产物


def asset_name(jar_basename):
    """本地 jar 名 → Release 统一命名。

    本地：`WurstB+ Plus-v1.5.0-Forge-1.21.11.jar` / `WurstB+ Plus-1.5.0-Fabric-1.20.1.jar`
    发布：`WurstB+.Plus-1.5.0-Forge-1.21.11.jar`（空格→点，去掉版本号前的 v）

    刻意从**实际产物的文件名**推导，而不是从 gradle.properties 的 mod_version 拼：
    26.3 三棵树的 mod_version 写法各不相同（`v1.5.0-Forge-26.3` / `1.5.0-Fabric-26.3`），
    拼出来会得到重复后缀。
    """
    name = jar_basename.replace(' ', '.')
    # Forge/NeoForge 的本地名是 `...-v1.5.0-...`，Fabric 的没有 v；发布名统一不带 v。
    return re.sub(r'([-.])v(?=\d)', r'\1', name)


def gradle_props(path):
    props = {}
    f = os.path.join(path, 'gradle.properties')
    if not os.path.isfile(f):
        return props
    for line in open(f, encoding='utf-8', errors='replace'):
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            k, v = line.split('=', 1)
            props[k.strip()] = v.strip()
    return props


def jdk_home(mc):
    """与 scripts/smoke-launch.py 同口径：1.20.1–1.20.4 → 17，1.20.5/1.21.x
    → 21，26.x → 25。老工程的 Gradle 8.x 跑在高版 JDK 上会死在
    `Unsupported class file major version`，与模组本身无关。"""
    if mc in ('1.20.1', '1.20.2', '1.20.3', '1.20.4'):
        want = 17
    elif mc.startswith(('1.20', '1.21')):
        want = 21
    else:
        want = 25
    env = os.environ.get(JDK_ENV[want])
    if env and os.path.isdir(env):
        return env
    for c in JDK_FALLBACK[want]:
        if os.path.isdir(c):
            return c
    return None


def gradle_command(project_dir):
    """有 wrapper jar 就用 wrapper，否则回退到本机同版本 Gradle 发行版。"""
    wrapper = os.path.join(project_dir, 'gradlew.bat')
    if os.path.isfile(os.path.join(project_dir, 'gradle', 'wrapper',
                                   'gradle-wrapper.jar')):
        return wrapper
    prop = os.path.join(project_dir, 'gradle', 'wrapper',
                        'gradle-wrapper.properties')
    dist = None
    if os.path.isfile(prop):
        m = re.search(r'gradle-(\d+\.\d+(?:\.\d+)?)-bin\.zip',
                      open(prop, encoding='utf-8', errors='replace').read())
        if m:
            dist = 'gradle-' + m.group(1) + '-bin'
    if dist:
        base = os.path.join(os.path.expanduser('~'), '.gradle', 'wrapper',
                            'dists', dist)
        for dirpath, _dirnames, filenames in os.walk(base):
            if 'gradle.bat' in filenames:
                return os.path.join(dirpath, 'gradle.bat')
    return wrapper


def projects():
    """(工程名, 目录, 加载器, mc) —— 与 RELEASE.md 的 63 个重建目标一致。"""
    out = []
    for base, loader in (('versions', 'Forge'), ('fabric/versions', 'Fabric'),
                         ('neoforge/versions', 'NeoForge')):
        d = os.path.join(ROOT, base)
        if not os.path.isdir(d):
            continue
        for name in sorted(os.listdir(d)):
            p = os.path.join(d, name)
            if not os.path.isdir(p) or name in SKIP_MC:
                continue
            mc = gradle_props(p).get('minecraft_version', name)
            out.append(('%s-%s' % (loader, mc), p, loader, mc))
    for sub, loader in (('fabric', 'Fabric'), ('neoforge', 'NeoForge')):
        p = os.path.join(ROOT, sub)
        mc = gradle_props(p).get('minecraft_version', '1.20.1')
        out.append(('%s-%s' % (loader, mc), p, loader, mc))
    return out


def task_for(loader, mc):
    """生产任务，规则来自 docs/RELEASE.md 的「发布产物矩阵」表。

    Fabric 用 `remapJar` 而不是 `build`：`build` 会顺带跑 `validateAccessWidener`，
    而 1.20.5 / 1.20.6 的 `wurstpenguin.accesswidener` 里有一条陈旧声明
    （`Entity.maxUpStep` 字段在这两个版本已不存在），会让 `build` 直接失败。
    这不是脚本问题，也与最终 jar 无关——`build` 里的 `remapJar` 产物是一样的。
    """
    if loader == 'Forge':
        return 'jarJar' if mc in ('1.20.2', '1.20.3', '1.20.4', '1.20.6',
                                  '1.21', '1.21.1') else 'allJar'
    if loader == 'Fabric':
        return 'remapJar'
    return 'build'


def find_jar(project_dir, loader, mc):
    libs = os.path.join(project_dir, 'build', 'libs')
    if not os.path.isdir(libs):
        return None
    cands = []
    for f in os.listdir(libs):
        if not f.endswith('.jar') or 'sources' in f or f.endswith('-dev.jar'):
            continue
        if mc not in f or loader not in f:
            continue
        cands.append(os.path.join(libs, f))
    if not cands:
        return None
    return max(cands, key=os.path.getmtime)


def build(name, project_dir, loader, mc):
    task = task_for(loader, mc)
    home = jdk_home(mc)
    env = dict(os.environ)
    if home:
        env['JAVA_HOME'] = home
    os.makedirs(LOGS, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    log = os.path.join(LOGS, name + '.log')
    cmd = [gradle_command(project_dir), task, '--init-script', INIT,
           '--console=plain']
    if project_dir.endswith('fabric') or 'fabric' + os.sep in project_dir + os.sep:
        cmd.append('-Ploom_libraries_base=https://libraries.minecraft.net/')
    start = time.time()
    with open(log, 'w', encoding='utf-8', errors='replace') as fh:
        rc = subprocess.call(cmd, cwd=project_dir, stdout=fh,
                             stderr=subprocess.STDOUT, env=env)
    el = time.time() - start
    if rc != 0:
        return ('FAIL', el, 'gradle rc=%d，见 %s' % (rc, os.path.basename(log)))
    jar = find_jar(project_dir, loader, mc)
    if not jar:
        return ('FAIL', el, '找不到产物 jar')
    dest = os.path.join(OUT, asset_name(os.path.basename(jar)))
    shutil.copy2(jar, dest)
    return ('OK', el, '%s → %s (%.1f MB)' % (
        os.path.basename(jar), os.path.basename(dest),
        os.path.getsize(dest) / 1e6))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('only', nargs='*', help='只构建这些工程（如 Forge-1.21.5）')
    ap.add_argument('--plan', action='store_true', help='只打印计划')
    ap.add_argument('--jobs', type=int, default=3,
                    help='并发构建数（默认 3；每个 Forge/NeoForge 构建约占 2GB，'
                         '按可用内存调）')
    args = ap.parse_args()

    all_projects = projects()
    targets = [p for p in all_projects if not args.only or p[0] in args.only]
    print('矩阵工程 %d 个，本次构建 %d 个' % (len(all_projects), len(targets)))
    if args.plan:
        for name, d, loader, mc in targets:
            print('  %-16s %-8s %-20s task=%-8s jdk=%s' % (
                name, loader, os.path.relpath(d, ROOT),
                task_for(loader, mc),
                os.path.basename(jdk_home(mc) or '?')))
        return 0

    os.makedirs(OUT, exist_ok=True)
    os.makedirs(LOGS, exist_ok=True)
    report = open(os.path.join(OUT, '_build-report.txt'), 'w',
                  encoding='utf-8')
    ok = fail = 0
    lock = threading.Lock()
    with ThreadPoolExecutor(max_workers=max(1, args.jobs)) as pool:
        futures = {pool.submit(build, *t): t for t in targets}
        for fut in as_completed(futures):
            name = futures[fut][0]
            verdict, el, detail = fut.result()
            line = '%-6s %-16s %6.0fs  %s' % (verdict, name, el, detail)
            with lock:
                print(line, flush=True)
                report.write(line + '\n')
                report.flush()
                ok += verdict == 'OK'
                fail += verdict != 'OK'
    report.close()
    print('\n成功 %d / 失败 %d' % (ok, fail))
    return 1 if fail else 0


if __name__ == '__main__':
    sys.exit(main())
