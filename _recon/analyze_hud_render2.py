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
    m = re.search(r'public void onRenderGUI\([^)]*\)\s*\{', t)
    body = ''
    if m:
        i = m.end(); depth = 1; j = i
        while j < len(t) and depth:
            if t[j] == '{': depth += 1
            elif t[j] == '}': depth -= 1
            j += 1
        body = t[i:j]
    flags = []
    if 'guiWidth()' in body or 'guiHeight()' in body: flags.append('GUI_DIM')
    if '.screen' in body: flags.append('SCREEN')
    if 'uiHidden' in body: flags.append('UIHIDDEN')
    if 'RenderSystem' in body or 'RenderSystem' in t: flags.append('GL')
    if 'context.item' in body or 'drawItem' in body: flags.append('ITEM')
    if 'blit' in body: flags.append('BLIT')
    if re.search(r'context\.fill\([^)]*0x[89A-Fa-f]', body): flags.append('BG')
    if flags:
        print('%-26s %s' % (cls, ','.join(flags)))
