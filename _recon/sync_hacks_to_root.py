# -*- coding: utf-8 -*-
"""Copy the 1.20.1-adapted new hacks into the v1.6.0 root project."""
import re
import shutil
from pathlib import Path

ROOT = Path(r'C:\Users\ui863\Documents\trae_projects\VAP')
SRC_HACKS = ROOT / 'fabric/src/main/java/net/wurstclient/hacks'
DST_HACKS = ROOT / 'src/main/java/net/wurstclient/hacks'
ROOT_LIST = ROOT / 'src/main/java/net/wurstclient/hack/HackList.java'
SRC_LIST = ROOT / 'fabric/src/main/java/net/wurstclient/hack/HackList.java'

root_classes = set()
for d in [ROOT / 'src/main/java/net/wurstclient/hacks',
          ROOT / 'common/src/main/java/net/wurstclient/hacks']:
    if d.exists():
        root_classes |= {p.stem for p in d.rglob('*Hack.java')}

src_text = SRC_LIST.read_text(encoding='utf-8')
fields = {}
for f in SRC_HACKS.rglob('*Hack.java'):
    cls = f.stem
    if cls in root_classes:
        continue
    shutil.copy2(f, DST_HACKS / f.name)
    m = re.search(r'public final ' + cls + r'\s+(\w+)\s*=', src_text)
    if m:
        fields[cls] = m.group(1)

text = ROOT_LIST.read_text(encoding='utf-8')
decls = list(re.finditer(r'^\s*public final \w+ \w+\s*=.*?;',
                         text, re.M | re.S))
if not decls:
    raise SystemExit('no fields found')
pos = decls[-1].end()
lines = ''.join('\n\tpublic final %s %s = new %s();' % (c, f, c)
                for c, f in sorted(fields.items()))
text = text[:pos] + lines + text[pos:]
ROOT_LIST.write_text(text, encoding='utf-8', newline='\n')
print('copied', len(fields), 'registered', len(fields))
