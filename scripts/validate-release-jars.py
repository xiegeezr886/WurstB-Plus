#!/usr/bin/env python3
r"""打包校验：逐个检查 build/release-v1.5/ 的产物是否是可用的发布 jar。

docs/RELEASE.md 记录当初用 tmp-recon/validate-jars.py（已随仓库外目录丢失）做过
「zip 完好 / 加载器元数据 / Mixin 配置 / 主类」四项校验。本脚本按同一口径重做：

  * zip 可完整读取（能列条目 = 中央目录没坏）
  * 该加载器自己的元数据存在（Fabric: fabric.mod.json；Forge: META-INF/mods.toml；
    NeoForge: META-INF/neoforge.mods.toml）
  * Wurst 自己的 Mixin 配置存在（wurstpenguin.mixins.json 或 wurst.mixins.json）
  * 至少有一个 .class（能加载的模组不可能没有）

用法：python scripts/validate-release-jars.py
"""
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DIST = os.path.join(ROOT, 'build', 'release-v1.5')

LOADER_META = {
    'Fabric': ('fabric.mod.json',),
    'Forge': ('META-INF/mods.toml',),
    # NeoForge 1.20.1 仍沿用从 Forge 继承的 `mods.toml`，
    # `neoforge.mods.toml` 是后来版本才改的名，两种都算合格。
    'NeoForge': ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml'),
}
MIXIN_CFGS = ('wurstpenguin.mixins.json', 'wurst.mixins.json')


def check(path):
    name = os.path.basename(path)
    problems = []
    loader = next((l for l in LOADER_META if '-%s-' % l in name), None)
    if loader is None:
        return ['文件名里看不出加载器']

    with zipfile.ZipFile(path) as z:
        bad = z.testzip()
        if bad:
            problems.append('zip 损坏于 %s' % bad)
        names = set(z.namelist())
        if not any(m in names for m in LOADER_META[loader]):
            problems.append('缺加载器元数据 %s'
                            % ('/'.join(LOADER_META[loader]),))
        if not any(m in names for m in MIXIN_CFGS):
            problems.append('缺 Mixin 配置')
        if not any(n.endswith('.class') for n in names):
            problems.append('没有任何 .class')
    return problems


def main():
    jars = sorted(f for f in os.listdir(DIST) if f.endswith('.jar'))
    print('待校验 %d 个产物\n' % len(jars))
    bad = 0
    for f in jars:
        problems = check(os.path.join(DIST, f))
        if problems:
            bad += 1
            print('  FAIL %-46s %s' % (f, '; '.join(problems)))
    print('\n合格 %d / 不合格 %d' % (len(jars) - bad, bad))
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
