# -*- coding: utf-8 -*-
import hashlib
import json
import re
from pathlib import Path

projects = []
projects += sorted(Path('fabric/versions').glob('*'))
projects.append(Path('fabric'))
projects += sorted(Path('neoforge/versions').glob('*'))
projects.append(Path('neoforge'))
projects += sorted(Path('versions').glob('*'))

print('== hud2 presence / hud editor / hashes ==')
hashes = {}
for p in projects:
    hm = p / 'src/main/java/net/wurstclient/hud2/HudManager.java'
    he = p / 'src/main/java/net/wurstclient/hud2/HudEditorScreen.java'
    th = p / 'src/main/java/net/wurstclient/hud2/elements/TextHudElement.java'
    if not hm.exists():
        continue
    h = hashlib.md5(hm.read_bytes()).hexdigest()[:8]
    hashes.setdefault(h, []).append(str(p))
    print('%-34s hm=%s editor=%s text=%s' % (p, h, he.exists(), th.exists()))
print('distinct HudManager hashes:', len(hashes))

print('\n== GUIRenderListener hacks with Position X/Y ==')
hacks = Path('fabric/versions/1.21.11/src/main/java/net/wurstclient/hacks')
rows = []
for f in sorted(hacks.glob('*Hack.java')):
    t = f.read_text(encoding='utf-8', errors='ignore')
    if 'implements GUIRenderListener' not in t:
        continue
    has_x = re.search(r'new (?:SliderSetting|TextBoxSetting|NumberSetting)\("Position X"', t)
    has_y = re.search(r'new (?:SliderSetting|TextBoxSetting|NumberSetting)\("Position Y"', t)
    if has_x and has_y:
        name = re.search(r'super\("([^"]+)"', t)
        rows.append((f.stem[:-4], name.group(1) if name else '?'))
print('count', len(rows))
for c, n in rows:
    print('%-28s %s' % (c, n))
