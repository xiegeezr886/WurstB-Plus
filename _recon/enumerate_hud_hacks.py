# -*- coding: utf-8 -*-
import re
from pathlib import Path

root = Path('fabric/versions/1.21.11/src/main/java/net/wurstclient')
list_text = (root / 'hack/HackList.java').read_text(encoding='utf-8')
fields = re.findall(
    r'public final (\w+Hack)\s+(\w+)\s*=\s*new\s+\w+Hack\(', list_text)
print('fields', len(fields))
rows = []
for cls, field in fields:
    src = root / 'hacks' / (cls + '.java')
    if not src.exists():
        print('missing', cls)
        continue
    t = src.read_text(encoding='utf-8', errors='ignore')
    if ('new SliderSetting("Position X"' in t
            and 'new SliderSetting("Position Y"' in t):
        name = re.search(r'super\("([^"]+)"', t)
        rows.append((cls, field, name.group(1) if name else '?'))
print('auto-detected HUD hacks:', len(rows))
for cls, field, name in rows:
    print('%-26s %-32s %s' % (cls, field, name))
