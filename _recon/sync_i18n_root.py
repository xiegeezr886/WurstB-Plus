# -*- coding: utf-8 -*-
"""Sync hack names and setting names into the v1.6.0 root project."""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import add_setting_name_zh as S
import add_hack_name_zh as H

ROOT = Path(r'C:\Users\ui863\Documents\trae_projects\VAP')
ZH_FILE = ROOT / 'src/main/resources/assets/wurst/translations/zh_cn.json'
HACK_DIRS = [ROOT / 'src/main/java/net/wurstclient/hacks',
             ROOT / 'common/src/main/java/net/wurstclient/hacks']


def main():
    zh = json.loads(ZH_FILE.read_text(encoding='utf-8'))
    add = {}

    # 1) hack display names
    for d in HACK_DIRS:
        if not d.exists():
            continue
        for f in d.rglob('*Hack.java'):
            cls = f.stem
            if cls not in H.ZH:
                continue
            t = f.read_text(encoding='utf-8', errors='ignore')
            m = re.search(r'super\("([^"]+)"\)', t)
            if not m:
                continue
            key = 'hack.name.' + m.group(1).lower()
            if key not in zh:
                add[key] = H.ZH[cls]

    # 2) setting names
    names = set()
    for d in HACK_DIRS:
        if not d.exists():
            continue
        for f in d.rglob('*Hack.java'):
            t = f.read_text(encoding='utf-8', errors='ignore')
            for m in re.finditer(r'new\s+\w*Setting(?:<[^>]*>)?\(\s*"([^"]+)"', t):
                names.add(m.group(1))
    for n in names:
        key = 'setting.name.' + S.snake(n)
        if key in zh:
            continue
        val, _ = S.translate(n)
        if val:
            add[key] = val

    if not add:
        print('nothing to add')
        return
    text = ZH_FILE.read_text(encoding='utf-8')
    lines = text.splitlines(keepends=True)
    anchor = None
    for i, line in enumerate(lines):
        if '"setting.name.' in line:
            anchor = i
            break
    if anchor is None:
        for i, line in enumerate(lines):
            if '"description.wurst.hack.' in line:
                anchor = i
                break
    if anchor is None:
        print('no anchor')
        return
    if not lines[anchor].rstrip().endswith(','):
        lines[anchor] = lines[anchor].rstrip('\r\n') + ',\n'
    ins = ['"%s": "%s",\n' % (k, v) for k, v in add.items()]
    lines[anchor + 1:anchor + 1] = ins
    new = ''.join(lines)
    json.loads(new)
    ZH_FILE.write_text(new, encoding='utf-8', newline='\n')
    print('added', len(add), 'keys')


if __name__ == '__main__':
    main()
