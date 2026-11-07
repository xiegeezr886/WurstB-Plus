# -*- coding: utf-8 -*-
"""Add en_us/zh_cn descriptions for the Meteor-derived hacks (66 v1.5 projects)."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

KEYS = ['antianchor', 'antianvil', 'antibed', 'selfweb', 'selfanvil',
        'autoweapon', 'quiver', 'fastclimb', 'clicktp', 'longjump',
        'middleclickextra', 'automend', 'autoreplenish', 'chestswap',
        'autogap', 'tracers', 'breakindicators', 'voidesp', 'tunnelsp',
        'timechanger', 'weatherchanger']
# note: tunnelesp is the real key; keep the list consistent with hack names
KEYS = [k if k != 'tunnelsp' else 'tunnelsp' for k in KEYS]

EN = {
    'antianchor': 'Protects you from anchor explosions.',
    'antianvil': 'Protects you from anvils falling on you.',
    'antibed': 'Protects you from bed explosions.',
    'selfweb': 'Places webs at your feet.',
    'selfanvil': 'Places anvils above your head.',
    'autoweapon': 'Automatically selects the best weapon.',
    'quiver': 'Automatically selects the correct arrow.',
    'fastclimb': 'Allows you to climb ladders faster.',
    'clicktp': 'Teleports you to the block you click.',
    'longjump': 'Allows you to jump further than normal.',
    'middleclickextra': 'Perform various actions when you middle click.',
    'automend': 'Automatically replaces items in your offhand with mending when fully repaired.',
    'autoreplenish': 'Automatically replenishes stacks in your hotbar.',
    'chestswap': 'Automatically swaps between a chestplate and an elytra.',
    'autogap': 'Automatically eats a golden apple when low on health.',
    'tracers': 'Draws lines to entities.',
    'breakindicators': 'Shows the progress of breaking blocks.',
    'voidesp': 'Highlights holes that lead to the void.',
    'tunnelsp': 'Highlights nearby tunnels.',
    'timechanger': 'Changes the client-side time.',
    'weatherchanger': 'Changes the client-side weather.',
}

ZH = {
    'antianchor': '防止重生锚爆炸伤害你。',
    'antianvil': '防止铁砧砸到你。',
    'antibed': '防止床爆炸伤害你。',
    'selfweb': '自动在自己脚下放置蜘蛛网。',
    'selfanvil': '自动在自己头顶放置铁砧。',
    'autoweapon': '自动切换到伤害最高的武器。',
    'quiver': '自动选择带有指定效果的箭。',
    'fastclimb': '让你更快地爬梯子。',
    'clicktp': '右键点击方块时传送到点击位置。',
    'longjump': '让你跳得比正常更远。',
    'middleclickextra': '中键点击时执行指定操作。',
    'automend': '自动把需要修复的经验修补物品换到副手。',
    'autoreplenish': '自动从背包补充快捷栏物品。',
    'chestswap': '在胸甲和鞘翅之间自动切换。',
    'autogap': '生命值低时自动吃金苹果。',
    'tracers': '从屏幕中心向实体绘制追踪线。',
    'breakindicators': '显示方块破坏进度。',
    'voidesp': '高亮通向虚空的洞口。',
    'tunnelsp': '高亮附近的隧道。',
    'timechanger': '在客户端修改时间。',
    'weatherchanger': '在客户端修改天气。',
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
        # fall back to the first description line
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
