# -*- coding: utf-8 -*-
"""Copy hack description translations from the 1.20.1 v1.5 project to root."""
import json
import re
from pathlib import Path

ROOT = Path(r'C:\Users\ui863\Documents\trae_projects\VAP')
ROOT_ZH = ROOT / 'src/main/resources/assets/wurst/translations/zh_cn.json'
SRC_ZH = ROOT / 'fabric/src/main/resources/assets/wurst/translations/zh_cn.json'
HACK_DIRS = [ROOT / 'src/main/java/net/wurstclient/hacks']

root_zh = json.loads(ROOT_ZH.read_text(encoding='utf-8'))
src_zh = json.loads(SRC_ZH.read_text(encoding='utf-8'))
add = {}
for d in HACK_DIRS:
    for f in d.rglob('*Hack.java'):
        t = f.read_text(encoding='utf-8', errors='ignore')
        m = re.search(r'super\("([^"]+)"\)', t)
        if not m:
            continue
        key = 'description.wurst.hack.' + m.group(1).lower()
        if key in root_zh or key not in src_zh:
            continue
        add[key] = src_zh[key]

if not add:
    print('nothing to add')
    raise SystemExit
text = ROOT_ZH.read_text(encoding='utf-8')
lines = text.splitlines(keepends=True)
anchor = next(i for i, l in enumerate(lines)
              if '"description.wurst.hack.' in l)
if not lines[anchor].rstrip().endswith(','):
    lines[anchor] = lines[anchor].rstrip('\r\n') + ',\n'
lines[anchor + 1:anchor + 1] = [
    '%s: %s,\n' % (json.dumps(k, ensure_ascii=False),
                   json.dumps(v, ensure_ascii=False))
    for k, v in add.items()]
new = ''.join(lines)
json.loads(new)
ROOT_ZH.write_text(new, encoding='utf-8', newline='\n')
print('added descriptions', len(add))
