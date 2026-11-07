# -*- coding: utf-8 -*-
"""zh_cn names/descriptions for the fifth Soar batch."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

NAMES = {
    'blockoverlay': ('方块覆盖', '为瞄准的方块绘制填充与描边覆盖层。'),
    'chunkborders': ('区块边界', '显示附近区块的边界线。'),
    'targetindicator': ('目标指示', '在攻击目标周围绘制指示圆环。'),
    'bloodparticles': ('血液粒子', '实体受伤时生成红色粒子。'),
    'damageparticles': ('伤害粒子', '实体受伤时生成火花粒子。'),
    'nametag': ('自定义名牌', '在实体上方绘制自定义名字标签。'),
    'modernhotbar': ('现代快捷栏', '绘制带高亮和状态条的现代快捷栏。'),
    'tabeditor': ('Tab 编辑', '自定义玩家列表布局与显示内容。'),
    'playerdisplay': ('玩家列表', 'HUD 显示附近玩家列表。'),
    'playerpredicator': ('玩家预测', '根据速度预测并显示附近玩家的位置。'),
}


def add(file, entries):
    text = file.read_text(encoding='utf-8')
    missing = [(k, v) for k, v in entries if '"%s"' % k not in text]
    if not missing:
        return 0
    lines = text.splitlines(keepends=True)
    anchor = next((i for i, l in enumerate(lines)
                   if '"description.wurst.hack.' in l), None)
    if anchor is None:
        return -1
    if not lines[anchor].rstrip().endswith(','):
        lines[anchor] = lines[anchor].rstrip('\r\n') + ',\n'
    lines[anchor + 1:anchor + 1] = [
        '%s: %s,\n' % (json.dumps(k, ensure_ascii=False),
                       json.dumps(v, ensure_ascii=False))
        for k, v in missing]
    new = ''.join(lines)
    json.loads(new)
    file.write_text(new, encoding='utf-8', newline='\n')
    return len(missing)


def main():
    root = REPO.resolve()
    stats = {}
    for p in project_dirs():
        if p.resolve() == root:
            continue
        zh = p / 'src/main/resources/assets/wurst/translations/zh_cn.json'
        if not zh.exists():
            continue
        entries = []
        for k, (name, desc) in NAMES.items():
            entries.append(('hack.name.' + k, name))
            entries.append(('description.wurst.hack.' + k, desc))
        n = add(zh, entries)
        stats[n] = stats.get(n, 0) + 1
    print('soar5 i18n', stats)


if __name__ == '__main__':
    main()
