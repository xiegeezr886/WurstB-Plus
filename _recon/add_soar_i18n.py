# -*- coding: utf-8 -*-
"""Add zh_cn names/descriptions for the 15 Soar batch-1 hacks."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

DATA = {
    'bettertab': ('更好的 Tab', '自定义玩家列表的显示内容。'),
}

NAMES = {
    'godbridgeassist': ('搭桥辅助', '自动在脚下放方块辅助搭桥。'),
    'quickswitch': ('快捷切换', '根据场景快速切换到剑、镐、方块或金苹果。'),
    'togglesneak': ('切换潜行', '按一下潜行键后持续潜行。'),
    'togglesprint': ('切换疾跑', '按一下疾跑键后持续疾跑。'),
    'taplook': ('快速视角', '按键快速转向预设方向。'),
    'bowzoom': ('弓缩放', '拉弓时自动缩放视野。'),
    'fovmodifier': ('视野修改', '自定义基础视野。'),
    'asyncscreenshot': ('异步截图', '在后台线程截图，避免卡顿。'),
    'borderlessfullscreen': ('无边框全屏', '切换无边框全屏。'),
    'fpslimiter': ('帧率限制', '限制游戏最大帧率。'),
    'autotext': ('自动文本', '按键或定时发送预设文本。'),
    'hypixelquickplay': ('Hypixel 快速开局', '一键发送 /play 命令快速开局。'),
    'combocounter': ('连击计数', 'HUD 显示连续命中次数。'),
    'keystrokes': ('按键显示', 'HUD 显示 WASD、空格、潜行和鼠标按键。'),
    'stopwatch': ('秒表', 'HUD 秒表。'),
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
    print('soar i18n', stats)


if __name__ == '__main__':
    main()
