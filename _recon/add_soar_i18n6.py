# -*- coding: utf-8 -*-
"""zh_cn names/descriptions for Chat/Waypoint."""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

NAMES = {
    'chat': ('聊天增强', '聊天防清屏、时间戳、历史记录与防刷屏。'),
    'waypoint': ('路径点', 'HUD 显示路径点列表与方向，可绘制光柱。'),
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
    print('soar6 i18n', stats)


if __name__ == '__main__':
    main()
