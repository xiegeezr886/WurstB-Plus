# -*- coding: utf-8 -*-
"""zh_cn names/descriptions for the second Soar HUD batch."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

NAMES = {
    'appleskin': ('苹果皮', '显示饥饿、饱和度和手持食物的营养。'),
    'armorstatus': ('护甲状态', '显示各护甲槽位的耐久与百分比。'),
    'potionstatus': ('药水状态', '显示当前药水效果与剩余时间。'),
    'bosshealth': ('Boss 血量', '显示 Boss 名称与血量条。'),
    'uhcoverlay': ('UHC 覆盖层', '显示生命值、伤害吸收与回复信息。'),
    'targetinfo': ('目标信息', '显示准星目标的名字、血量与距离。'),
    'tnttimer': ('TNT 计时', '显示附近点燃 TNT 的引信时间。'),
    'horsestats': ('马匹属性', '显示马匹的速度、跳跃与生命值。'),
    'sessioninfo': ('会话信息', '显示本次游玩的时长、进出服与击杀。'),
    'cpsdisplay': ('CPS 显示', '显示左右键每秒点击次数。'),
    'mousestrokes': ('鼠标按键', '显示鼠标左右键状态。'),
    'reachdisplay': ('触及显示', '显示最近一次攻击/交互的距离。'),
    'playercounter': ('玩家计数', '显示在线玩家数量。'),
    'speedometer': ('速度计', '显示移动速度（格/秒）。'),
    'blockinfo': ('方块信息', '显示准星方块的名称、坐标与硬度。'),
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
    print('soar2 i18n', stats)


if __name__ == '__main__':
    main()
