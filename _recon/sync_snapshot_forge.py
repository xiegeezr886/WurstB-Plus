# -*- coding: utf-8 -*-
"""Sync the 36 new modules into the Forge 1.20.1 snapshot only."""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO
from apply_soar_remaining import FIELDS
from patch_root_1_20_1 import patch_root
from patch_v8_modules import patch_player, patch_key

SNAP = Path(r'C:\Users\ui863\Documents\trae_projects\VAP-v1.5.0-1.20.1')
MAIN = REPO

# Copy new hack classes, except EntityCulling which already exists.
src_hacks = MAIN / 'src/main/java/net/wurstclient/hacks'
dst_hacks = SNAP / 'src/main/java/net/wurstclient/hacks'
copied = skipped = 0
for cls in FIELDS:
    src = src_hacks / (cls + '.java')
    dst = dst_hacks / (cls + '.java')
    if not src.exists():
        print('MISSING SOURCE', cls)
        continue
    if dst.exists():
        skipped += 1
        continue
    dst.write_text(src.read_text(encoding='utf-8'), encoding='utf-8',
                   newline='\n')
    copied += 1

# Register fields after the existing EntityCulling field.
list_path = SNAP / 'src/main/java/net/wurstclient/hack/HackList.java'
text = list_path.read_text(encoding='utf-8')
anchor = ('\tpublic final EntityCullingHack entityCullingHack = '
          'new EntityCullingHack();')
if anchor not in text:
    raise SystemExit('HackList anchor not found')
added = 0
for cls, field in FIELDS.items():
    if field in text:
        continue
    line = '\n\tpublic final %s %s = new %s();' % (cls, field, cls)
    text = text.replace(anchor, anchor + line, 1)
    anchor = anchor + line
    added += 1
list_path.write_text(text, encoding='utf-8', newline='\n')

# Patch existing mixins.
mixins = SNAP / 'src/main/java/net/wurstclient/mixin'
stats = patch_root(SNAP)
player = mixins / 'PlayerMixin.java'
if player.exists() and patch_player(player):
    stats['PlayerMixin/ForceMainHand'] = 1
key = mixins / 'KeyBindingMixin.java'
if key.exists() and patch_key(key):
    stats['KeyBindingMixin/SnapTap'] = 1

# Merge all missing hack/setting translations from the main root. Existing
# snapshot values are preserved.
root_zh = json.loads((MAIN / 'src/main/resources/assets/wurst/translations/'
                      'zh_cn.json').read_text(encoding='utf-8'))
snap_zh_path = (SNAP / 'src/main/resources/assets/wurst/translations/'
                'zh_cn.json')
snap_zh = json.loads(snap_zh_path.read_text(encoding='utf-8'))
missing = [(k, v) for k, v in root_zh.items()
           if (k.startswith('hack.name.')
               or k.startswith('description.wurst.hack.')
               or k.startswith('setting.name.'))
           and k not in snap_zh]
if missing:
    lines = snap_zh_path.read_text(encoding='utf-8').splitlines(keepends=True)
    anchor_i = next((i for i, l in enumerate(lines)
                     if '"setting.name.' in l or
                     '"description.wurst.hack.' in l), None)
    if anchor_i is None:
        raise SystemExit('translation anchor not found')
    if not lines[anchor_i].rstrip().endswith(','):
        lines[anchor_i] = lines[anchor_i].rstrip('\r\n') + ',\n'
    lines[anchor_i + 1:anchor_i + 1] = [
        '%s: %s,\n' % (json.dumps(k, ensure_ascii=False),
                       json.dumps(v, ensure_ascii=False))
        for k, v in missing]
    new = ''.join(lines)
    json.loads(new)
    snap_zh_path.write_text(new, encoding='utf-8', newline='\n')

print('copied', copied, 'skipped', skipped, 'fields', added,
      'translations', len(missing), 'mixins', stats)
