# -*- coding: utf-8 -*-
"""Add descriptions for Meteor batch-3 hacks."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

KEYS = ['autonametag', 'autoshearer', 'autobrewer', 'echestfarmer',
        'flamethrower', 'liquidfiller', 'highwaybuilder', 'infinityminer',
        'noghostblocks', 'packetmine', 'spawnproofer', 'stashfinder',
        'entityowner', 'fakeplayer', 'norotate', 'bettertab',
        'bettertooltips', 'zoom', 'nostatuseffects', 'betterbeacons',
        'bookbot', 'inventorytweaks', 'messageaura', 'notifier']

EN = {
    'autonametag': 'Automatically uses name tags on entities.',
    'autoshearer': 'Automatically shears nearby sheep.',
    'autobrewer': 'Automates brewing stands.',
    'echestfarmer': 'Automatically places and breaks ender chests.',
    'flamethrower': 'Sets nearby targets on fire.',
    'liquidfiller': 'Fills liquids with blocks.',
    'highwaybuilder': 'Builds a highway in one direction.',
    'infinityminer': 'Continuously mines blocks.',
    'noghostblocks': 'Removes client-side ghost blocks.',
    'packetmine': 'Mines blocks using packets.',
    'spawnproofer': 'Places blocks to prevent mob spawning.',
    'stashfinder': 'Finds storage blocks in loaded chunks.',
    'entityowner': 'Shows the owner of tamed entities.',
    'fakeplayer': 'Spawns a client-side fake player.',
    'norotate': 'Prevents the server from rotating your view.',
    'bettertab': 'Improves the player list.',
    'bettertooltips': 'Improves item tooltips.',
    'zoom': 'Zooms the camera.',
    'nostatuseffects': 'Hides status effect icons.',
    'betterbeacons': 'Improves the beacon screen.',
    'bookbot': 'Automatically writes books.',
    'inventorytweaks': 'Sorts and merges your inventory.',
    'messageaura': 'Sends messages periodically.',
    'notifier': 'Notifies you about important events.',
}

ZH = {
    'autonametag': '自动对实体使用命名牌。',
    'autoshearer': '自动剪附近羊的羊毛。',
    'autobrewer': '自动酿造药水。',
    'echestfarmer': '自动放置并破坏末影箱。',
    'flamethrower': '点燃附近的目标。',
    'liquidfiller': '用方块填充液体。',
    'highwaybuilder': '自动修建直线高速公路。',
    'infinityminer': '持续自动挖矿。',
    'noghostblocks': '清除客户端的幽灵方块。',
    'packetmine': '使用数据包快速挖掘方块。',
    'spawnproofer': '放置方块阻止怪物生成。',
    'stashfinder': '在已加载区块中寻找储物方块。',
    'entityowner': '显示已驯服实体的主人。',
    'fakeplayer': '生成客户端假玩家。',
    'norotate': '阻止服务器旋转你的视角。',
    'bettertab': '改进玩家列表。',
    'bettertooltips': '改进物品提示。',
    'zoom': '缩放视角。',
    'nostatuseffects': '隐藏状态效果图标。',
    'betterbeacons': '改进信标界面。',
    'bookbot': '自动写书。',
    'inventorytweaks': '整理并合并背包物品。',
    'messageaura': '定时发送消息。',
    'notifier': '重要事件通知。',
}


def add_keys(path, table):
    text = path.read_text(encoding='utf-8')
    missing = [k for k in KEYS
               if '"description.wurst.hack.%s"' % k not in text]
    if not missing:
        return 0
    lines = text.splitlines(keepends=True)
    anchor = None
    for i, line in enumerate(lines):
        if '"description.wurst.hack.antiknockback"' in line:
            anchor = i
            break
    if anchor is None:
        for i, line in enumerate(lines):
            if '"description.wurst.hack.' in line:
                anchor = i
                break
    if anchor is None:
        return -1
    if not lines[anchor].rstrip().endswith(','):
        lines[anchor] = lines[anchor].rstrip('\r\n') + ',\n'
    ins = ['"description.wurst.hack.%s": "%s",\n' % (k, table[k])
           for k in missing]
    lines[anchor + 1:anchor + 1] = ins
    new = ''.join(lines)
    json.loads(new)
    path.write_text(new, encoding='utf-8', newline='\n')
    return len(missing)


def main():
    root = REPO.resolve()
    stats = {}
    for p in project_dirs():
        if p.resolve() == root:
            continue
        base = p / 'src/main/resources/assets/wurst/translations'
        for name, table in (('en_us.json', EN), ('zh_cn.json', ZH)):
            f = base / name
            if f.exists():
                n = add_keys(f, table)
                stats[n] = stats.get(n, 0) + 1
    print('translations3', stats)


if __name__ == '__main__':
    main()
