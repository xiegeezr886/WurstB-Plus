# -*- coding: utf-8 -*-
import re
from pathlib import Path

hacks = Path('fabric/versions/1.21.11/src/main/java/net/wurstclient/hacks')
res = []
for f in sorted(hacks.glob('*Hack.java')):
    t = f.read_text(encoding='utf-8', errors='ignore')
    if 'implements GUIRenderListener' not in t:
        continue
    if not (re.search(r'new SliderSetting\("Position X"', t)
            and re.search(r'new SliderSetting\("Position Y"', t)):
        continue
    m = re.search(r'public void onRenderGUI\(GuiGraphicsExtractor \w+,\s*float \w+\)\s*\{', t)
    body = ''
    if m:
        i = m.end()
        depth = 1
        j = i
        while j < len(t) and depth:
            if t[j] == '{':
                depth += 1
            elif t[j] == '}':
                depth -= 1
            j += 1
        body = t[i:j]
    has_ui_hidden = 'uiHidden()' in t
    checks_screen = bool(re.search(r'MC\.screen|minecraft\.screen|\.screen\s*!=', body))
    has_bg = bool(re.search(r'new CheckboxSetting\(\s*"Background"', t))
    uses_flat = 'FlatRenderer' in t
    uses_items = 'drawItem' in t or '.item(' in body or 'renderItem' in body or 'fakeItem' in body
    checks_enabled = 'isEnabled()' in body
    name = re.search(r'super\("([^"]+)"', t)
    res.append((f.stem[:-4], name.group(1) if name else '?', has_ui_hidden,
                checks_screen, has_bg, uses_flat, uses_items, checks_enabled,
                len(body)))
print('class,name,uiHidden,bodyScreen,bgSetting,flat,items,bodyIsEnabled,bodyLen')
for r in res:
    print('%s,%s,%s,%s,%s,%s,%s,%s,%d' % r)
