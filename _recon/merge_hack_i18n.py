# -*- coding: utf-8 -*-
"""Fill missing hack names (from root Forge) and 23 descriptions everywhere."""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

DESCRIPTIONS = {
    'anchor': '自动放置、充能并引爆重生锚攻击附近实体。',
    'antibot': '识别并忽略服务器中的假玩家或机器人。',
    'autoexp': '自动投掷经验瓶。',
    'autosmelter': '自动将可熔炼物品放入熔炉并取出产物。',
    'baritonecleararea': '使用 Baritone 清理指定区域。',
    'baritonemine': '使用 Baritone 自动挖掘指定方块。',
    'baritonetreebot': '使用 Baritone 自动砍树。',
    'baritonewalk': '使用 Baritone 前往指定坐标。',
    'bossstack': '将多个 Boss 血条堆叠显示。',
    'bowspam': '快速连续射箭。',
    'breadcrumbs': '记录并渲染走过的路径。',
    'dankbobbing': '自定义视角摇晃效果。',
    'expthrower': '自动投掷经验瓶。',
    'lightoverlay': '显示可刷怪方块的光照标记。',
    'logoutspots': '记录玩家退出时的位置。',
    'notebot': '自动演奏音符盒歌曲。',
    'packetcanceller': '取消指定的收发数据包。',
    'packetlogger': '记录收发的数据包。',
    'popchams': '图腾弹出时显示彩色模型。',
    'targetshader': '给当前战斗目标添加描边或发光效果。',
    'tunnelesp': '高亮隧道或洞穴区域。',
    'radialmenu': '径向快捷菜单，按住按键显示常用操作。',
    'seedoreesp': '根据世界种子预测并高亮矿石位置。',
    'seedstructureesp': '根据世界种子预测并高亮结构位置。',
    'twerk': '自动潜行和起身。',
    'vomit': '让玩家持续吐出物品或粒子。',
}


def insert_entries(path, entries, anchor_prefix):
    if not entries:
        return 0
    text = path.read_text(encoding='utf-8')
    lines = text.splitlines(keepends=True)
    anchor = next((i for i, l in enumerate(lines)
                   if anchor_prefix in l), None)
    if anchor is None:
        anchor = next((i for i, l in enumerate(lines)
                       if '"description.wurst.hack.' in l), None)
    if anchor is None:
        return -1
    if not lines[anchor].rstrip().endswith(','):
        lines[anchor] = lines[anchor].rstrip('\r\n') + ',\n'
    lines[anchor + 1:anchor + 1] = [
        '%s: %s,\n' % (json.dumps(k, ensure_ascii=False),
                       json.dumps(v, ensure_ascii=False))
        for k, v in entries]
    new = ''.join(lines)
    json.loads(new)
    path.write_text(new, encoding='utf-8', newline='\n')
    return len(entries)


def main():
    root = REPO.resolve()
    root_zh_path = root / 'src/main/resources/assets/wurst/translations/zh_cn.json'
    root_zh = json.loads(root_zh_path.read_text(encoding='utf-8'))

    name_entries = {k: v for k, v in root_zh.items()
                    if k.startswith('hack.name.')}
    desc_entries = {'description.wurst.hack.' + k: v
                    for k, v in DESCRIPTIONS.items()}

    total_names = total_desc = 0
    for project in project_dirs():
        zh_path = (project / 'src/main/resources/assets/wurst/translations/'
                   'zh_cn.json')
        if not zh_path.exists():
            continue
        zh = json.loads(zh_path.read_text(encoding='utf-8'))
        missing_names = [(k, v) for k, v in name_entries.items()
                         if k not in zh]
        missing_desc = [(k, v) for k, v in desc_entries.items()
                        if k not in zh]
        total_names += len(missing_names)
        total_desc += len(missing_desc)
        if missing_names:
            insert_entries(zh_path, missing_names, '"hack.name.')
        if missing_desc:
            insert_entries(zh_path, missing_desc,
                           '"description.wurst.hack.')

    print('inserted names', total_names, 'descriptions', total_desc)


if __name__ == '__main__':
    main()
