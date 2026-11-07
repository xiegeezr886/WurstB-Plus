# -*- coding: utf-8 -*-
import re
from pathlib import Path

root = Path('fabric/versions/1.21.11/src/main/java/net/wurstclient')
list_text = (root / 'hack/HackList.java').read_text(encoding='utf-8')
fields = re.findall(r'public final (\w+Hack)\s+(\w+)\s*=\s*new\s+\w+Hack\(', list_text)
for cls, field in fields:
    src = root / 'hacks' / (cls + '.java')
    if not src.exists():
        continue
    t = src.read_text(encoding='utf-8', errors='ignore')
    if not ('new SliderSetting("Position X"' in t
            and 'new SliderSetting("Position Y"' in t):
        continue
    scales = re.findall(r'new SliderSetting\("([^"]*[Ss]cale[^"]*)"', t)
    sizes = re.findall(r'new SliderSetting\("(Size|Width|Height)"', t)
    if scales or sizes:
        print('%-26s scales=%s sizes=%s' % (cls, scales, sizes))
