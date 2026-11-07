# -*- coding: utf-8 -*-
"""zh_cn names/descriptions for the fourth Soar HUD batch."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

NAMES = {
    'calendar': ('日历', '在 HUD 显示当前日期与星期。'),
    'compass': ('指南针', '显示朝向与偏航角。'),
    'daycounter': ('天数计数', '显示游戏内天数。'),
    'gamemodedisplay': ('游戏模式显示', '显示当前游戏模式。'),
    'imagedisplay': ('图片显示', '在 HUD 显示本地图片。'),
    'inventorydisplay': ('背包显示', '显示背包物品网格。'),
    'iteminfo': ('物品信息', '显示手持物品的名称、数量与耐久。'),
    'memoryusage': ('内存占用', '显示 JVM 内存使用情况。'),
    'musicinfo': ('音乐信息', '显示当前播放的音乐。'),
    'namedisplay': ('名称显示', '显示当前账号名称。'),
    'playtimedisplay': ('游玩时间', '显示本次会话游玩时长。'),
    'potioncounter': ('药水计数', '统计药水效果与药水物品数量。'),
    'scoreboard': ('计分板', '显示自定义侧边计分板。'),
    'serveripdisplay': ('服务器地址', '显示当前服务器地址。'),
    'weatherdisplay': ('天气显示', '显示当前天气。'),
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
    print('soar4 i18n', stats)


if __name__ == '__main__':
    main()
