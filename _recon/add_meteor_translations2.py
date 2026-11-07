# -*- coding: utf-8 -*-
"""Add en_us/zh_cn descriptions for Meteor batch-2 hacks (66 v1.5 projects)."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

KEYS = ['arrowdodge', 'autoanvil', 'bedaura', 'offhand', 'airjump',
        'autojump', 'elytraboost', 'reversestep', 'autoclicker',
        'automount', 'betterchat', 'rotation', 'spam', 'blockesp',
        'blockselection', 'marker', 'trail', 'autobreed', 'autosmelt']

EN = {
    'arrowdodge': 'Tries to dodge arrows coming at you.',
    'autoanvil': 'Drops anvils on nearby enemies.',
    'bedaura': 'Automatically places and detonates beds.',
    'offhand': 'Automatically manages your offhand item.',
    'airjump': 'Allows you to jump in mid-air.',
    'autojump': 'Automatically jumps when running into blocks.',
    'elytraboost': 'Uses firework rockets to boost your elytra.',
    'reversestep': 'Quickly steps down blocks.',
    'autoclicker': 'Automatically clicks for you.',
    'automount': 'Automatically mounts nearby entities.',
    'betterchat': 'Improves the chat with anti-spam and history.',
    'rotation': 'Locks or silently rotates your view.',
    'spam': 'Repeatedly sends a chat message.',
    'blockesp': 'Highlights configured blocks.',
    'blockselection': 'Highlights the block you are looking at.',
    'marker': 'Places client-side markers.',
    'trail': 'Draws a trail behind you.',
    'autobreed': 'Automatically breeds nearby animals.',
    'autosmelt': 'Automatically moves items in a furnace.',
}

ZH = {
    'arrowdodge': '尝试躲避飞向你的箭。',
    'autoanvil': '在附近敌人头顶投放铁砧。',
    'bedaura': '自动放置并引爆床。',
    'offhand': '自动管理副手物品。',
    'airjump': '允许你在空中跳跃。',
    'autojump': '跑向方块时自动跳跃。',
    'elytraboost': '使用烟花火箭给鞘翅加速。',
    'reversestep': '快速从方块边缘下落。',
    'autoclicker': '自动为你点击。',
    'automount': '自动骑乘附近的实体。',
    'betterchat': '改进聊天（防刷屏、历史记录）。',
    'rotation': '锁定或静默旋转视角。',
    'spam': '重复发送聊天消息。',
    'blockesp': '高亮指定的方块。',
    'blockselection': '高亮你正在看的方块。',
    'marker': '放置客户端标记。',
    'trail': '在你身后绘制拖尾。',
    'autobreed': '自动繁殖附近的动物。',
    'autosmelt': '自动整理熔炉中的物品。',
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
    print('translations', stats)


if __name__ == '__main__':
    main()
