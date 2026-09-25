#!/usr/bin/env python3
"""预取某个 Minecraft 版本的游戏资源（assets），走国内镜像。

为什么需要它：Fabric 的 Loom 有镜像开关（见 gradle.properties 里的 loom_* 三项，
已在本仓库全部 fabric 工程里配好），但 **Forge（FG6/FG7）与 NeoForge
（NeoGradle/ModDevGradle）的插件没有这个开关**，它们的客户端在启动时会自己从
`resources.download.minecraft.net` 拉资源；国内直连经常断在半路，表现为启动日志里

    Failed to download minecraft/sounds/block/nether_ore/break2.ogg
    Caused by: java.io.IOException: https://resources.download.minecraft.net/40/40ab...

本脚本把资源提前用镜像拉齐，之后再启动客户端就不需要联网下载了。

资源是内容寻址的（文件名 = sha1），所以不同版本之间能共用：已经存在的文件会跳过，
只补缺的那些。多个版本换着启动，第二次开始通常只需要补很少的文件。

用法：
    python scripts/fetch-assets.py 26.2
    python scripts/fetch-assets.py 26.2 --jobs 16
    python scripts/fetch-assets.py 26.2 --index-only        # 只更新索引
    python scripts/fetch-assets.py 1.21.9 --assets-dir D:/mc-assets

默认资源目录取自 ForgeGradle 的位置（~/.gradle/caches/forge_gradle/assets），这是
本仓库 Forge / NeoForge 工程启动时用的目录。Fabric 工程用 Loom 自己的目录
（~/.gradle/caches/fabric-loom/assets），需要的话用 --assets-dir 指过去。
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
import re
import shutil
import sys
import time
import urllib.error
import urllib.request

# 镜像在前，官方兜底
MIRRORS = [
    'https://bmclapi2.bangbang93.com',
    'https://piston-meta.mojang.com',
]
ASSET_HOSTS = [
    'https://bmclapi2.bangbang93.com/assets',
    'https://resources.download.minecraft.net',
]
UA = {'User-Agent': 'WurstB-Plus asset fetcher'}


def log(msg):
    print(msg, flush=True)


def default_assets_dir():
    r"""资源目录。

    Forge 7 / NeoForge 的开发启动器复用原版启动器的目录（Windows 上是
    %APPDATA%\.minecraft\assets），实测确认：启动参数里就是
    `--assetsDir C:\Users\<你>\AppData\Roaming\.minecraft\assets`。
    Forge 6（本仓库根工程 1.20.1）用的是 ~/.gradle/caches/forge_gradle/assets，
    那种情况用 --assets-dir 指过去。
    """
    appdata = os.environ.get('APPDATA')
    if appdata:
        mc = os.path.join(appdata, '.minecraft', 'assets')
        if os.path.isdir(mc):
            return mc
    return os.path.join(os.path.expanduser('~'), '.gradle', 'caches',
                        'forge_gradle', 'assets')


def fetch(url, timeout=60, retries=3):
    last = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=timeout) as r:
                return r.read()
        except Exception as e:            # noqa: BLE001 - 网络错误种类多，统一重试
            last = e
            time.sleep(0.5 * (attempt + 1))
    raise last


def version_json(version):
    for host in MIRRORS:
        for path in ('/version/%s/json' % version, '/mc/game/%s.json' % version):
            try:
                raw = fetch(host + path)
                d = json.loads(raw)
                if 'assetIndex' in d:
                    log('版本 JSON 来源: %s%s' % (host, path))
                    return d
            except Exception:
                continue
    raise SystemExit('取不到 %s 的版本 JSON（镜像与官方都失败）' % version)


def asset_index(index_url):
    """索引是内容寻址的，把主机换成镜像即可。"""
    tail = index_url.split('piston-meta.mojang.com')[-1]
    for host in MIRRORS:
        try:
            return json.loads(fetch(host + tail))
        except Exception:
            continue
    raise SystemExit('取不到资源索引: %s' % index_url)


def sha1_of(path):
    h = hashlib.sha1()
    with open(path, 'rb') as f:
        for chunk in iter(lambda: f.read(1 << 16), b''):
            h.update(chunk)
    return h.hexdigest()


def repo_versions():
    """扫描仓库里所有工程的 gradle.properties，取出各自的目标 MC 版本。"""
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    found = []
    for dirpath, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in ('build', '.gradle')]
        if 'gradle.properties' not in files:
            continue
        try:
            with open(os.path.join(dirpath, 'gradle.properties'),
                      encoding='utf-8', errors='replace') as fh:
                text = fh.read()
        except OSError:
            continue
        m = re.search(r'^minecraft_version\s*=\s*(\S+)', text, re.M)
        if m and m.group(1) not in found:
            found.append(m.group(1))
    return found


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('version', nargs='?',
                    help='Minecraft 版本，例如 26.2；用 --all 时省略')
    ap.add_argument('--all', action='store_true',
                    help='扫描仓库里所有工程的目标版本，逐个补齐')
    ap.add_argument('--assets-dir', default=None)
    ap.add_argument('--jobs', type=int, default=12)
    ap.add_argument('--index-only', action='store_true')
    args = ap.parse_args()

    if args.all:
        versions = repo_versions()
        log('仓库里共 %d 个目标版本: %s' % (len(versions), ', '.join(versions)))
        for v in versions:
            log('')
            log('=' * 60)
            log('== %s' % v)
            log('=' * 60)
            try:
                run(v, args)
            except SystemExit as e:
                log('跳过 %s: %s' % (v, e))
        return

    if not args.version:
        raise SystemExit('需要指定版本，或用 --all')
    run(args.version, args)


def run(version, args):
    assets = args.assets_dir or default_assets_dir()
    objects = os.path.join(assets, 'objects')
    indexes = os.path.join(assets, 'indexes')
    os.makedirs(objects, exist_ok=True)
    os.makedirs(indexes, exist_ok=True)
    log('资源目录: %s' % assets)

    vj = version_json(version)
    ai = vj['assetIndex']
    idx_path = os.path.join(indexes, '%s.json' % ai['id'])
    raw = fetch(MIRRORS[0] + ai['url'].split('piston-meta.mojang.com')[-1])
    if hashlib.sha1(raw).hexdigest() != ai.get('sha1', ''):
        log('警告: 索引 sha1 与版本 JSON 不一致，仍写入')
    with open(idx_path, 'wb') as f:
        f.write(raw)
    log('已写入索引: %s (%d 字节, id=%s)' % (idx_path, len(raw), ai['id']))
    if args.index_only:
        return

    index = json.loads(open(idx_path, 'rb').read().decode('utf-8'))
    objs = index['objects']
    todo = []
    total_bytes = 0
    for name, meta in objs.items():
        h = meta['hash']
        p = os.path.join(objects, h[:2], h)
        if os.path.isfile(p) and os.path.getsize(p) == meta.get('size', -1):
            continue
        todo.append((h, meta.get('size', 0)))
        total_bytes += meta.get('size', 0)

    log('索引共 %d 个对象，本地已齐 %d 个，需要下载 %d 个 (%.1f MB)'
        % (len(objs), len(objs) - len(todo), len(todo), total_bytes / 1048576))
    if not todo:
        return

    done = [0]
    failed = []
    start = time.time()

    def grab(item):
        h, size = item
        dst = os.path.join(objects, h[:2], h)
        if os.path.isfile(dst) and os.path.getsize(dst) == size:
            return None
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        for host in ASSET_HOSTS:
            try:
                data = fetch('%s/%s/%s' % (host, h[:2], h), timeout=90)
                if size and len(data) != size:
                    raise IOError('大小不符 %d != %d' % (len(data), size))
                if sha1_of_bytes(data) != h:
                    raise IOError('sha1 不符')
                tmp = dst + '.part'
                with open(tmp, 'wb') as f:
                    f.write(data)
                shutil.move(tmp, dst)
                done[0] += 1
                if done[0] % 200 == 0 or done[0] == len(todo):
                    rate = done[0] / max(1e-9, time.time() - start)
                    log('  进度 %d/%d (%.0f 个/秒)' % (done[0], len(todo), rate))
                return None
            except Exception as e:        # noqa: BLE001
                last = e
        failed.append((h, repr(last)))
        return None

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.jobs) as ex:
        list(ex.map(grab, todo))

    log('完成: 新下载 %d 个, 失败 %d 个, 用时 %.0f 秒'
        % (done[0], len(failed), time.time() - start))
    for h, why in failed[:5]:
        log('  失败 %s: %s' % (h, why))
    if failed:
        sys.exit(1)


def sha1_of_bytes(data):
    return hashlib.sha1(data).hexdigest()


if __name__ == '__main__':
    main()
