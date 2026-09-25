#!/usr/bin/env python3
r"""修正 26.2 内嵌 Baritone 的混入配置里的 compatibilityLevel。

背景
----
`baritone-maven/` 被 .gitignore 排除，这些 jar 是本地产物，不在版本控制里。26.2 用的两个
baritone 构建（`1.18.0-26.2`）里的 `mixins.baritone.json` 声明了

    "compatibilityLevel": "JAVA_25"

而 Forge 26.2 自带的 Mixin 0.8.7 里 `MixinEnvironment$CompatibilityLevel` 枚举**最高只到
`JAVA_21`**，于是客户端在混入子系统初始化时直接崩掉，连主界面都到不了：

    org.spongepowered.asm.launch.MixinInitialisationError:
    Mixin config mixins.baritone.json specifies compatibility level JAVA_25
    which is not recognised

注意 baritone 的类确实是 Java 25 字节码（class major 69），所以这个值在语义上并不错；
只是这个 Mixin 版本不认识这个**名字**。全仓库其余 baritone jar（`1.17.0-*`、`1.20.0-26.3`）
都是 `JAVA_17`，26.2 这两个是异类。

用法
----
    python scripts/patch-baritone-mixin-level.py            # 就地修正
    python scripts/patch-baritone-mixin-level.py --check    # 只检查，返回码非 0 表示需要修

幂等：已经是 JAVA_21 的会跳过。改写用整包重写（zipfile），不用 ZipArchiveMode::Update——
后者产出的 jar 用 ZipInputStream 读不回来，`scripts/replace-jar-entry.ps1` 的注释里记录了
这个坑。
"""
import argparse
import json
import os
import shutil
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JARS = [
    'baritone-maven/baritone/baritone-forge/1.18.0-26.2/'
    'baritone-forge-1.18.0-26.2.jar',
    'baritone-maven/baritone/baritone-api-fabric/1.18.0-26.2/'
    'baritone-api-fabric-1.18.0-26.2.jar',
    'baritone-maven/baritone/baritone-neoforge/1.18.0-26.2/'
    'baritone-neoforge-1.18.0-26.2.jar',
]
TARGET = 'JAVA_21'


def patch(path, check_only):
    if not os.path.isfile(path):
        return 'skip', '不存在'
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        cfg = [n for n in names if n.endswith('mixins.baritone.json')]
        if not cfg:
            return 'skip', '没有 mixins.baritone.json'
        data = json.loads(z.read(cfg[0]).decode('utf-8'))
        level = data.get('compatibilityLevel')
        if level == TARGET:
            return 'ok', '已是 %s' % TARGET
        if check_only:
            return 'bad', '当前是 %s，需要改成 %s' % (level, TARGET)
        data['compatibilityLevel'] = TARGET
        new = json.dumps(data, indent=2, ensure_ascii=False).encode('utf-8')
        tmp = path + '.tmp'
        with zipfile.ZipFile(tmp, 'w', zipfile.ZIP_DEFLATED) as out:
            for n in names:
                out.writestr(n, new if n == cfg[0] else z.read(n))
    shutil.move(tmp, path)
    # 改完必须还能被 java.util.zip.ZipInputStream 读回来
    with zipfile.ZipFile(path) as z:
        if z.testzip() is not None:
            raise SystemExit('重写后的 jar 损坏: %s' % path)
    return 'fixed', '%s -> %s' % (level, TARGET)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--check', action='store_true')
    args = ap.parse_args()

    need = 0
    for rel in JARS:
        path = os.path.join(ROOT, rel)
        status, msg = patch(path, args.check)
        print('%-8s %-58s %s' % (status, rel.split('/')[-1], msg))
        if status == 'bad':
            need += 1
    if args.check and need:
        sys.exit(1)


if __name__ == '__main__':
    main()
