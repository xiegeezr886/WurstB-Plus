# -*- coding: utf-8 -*-
import re
from pathlib import Path

cur = Path('fabric/versions/1.21.11/src/main/java/net/wurstclient/hacks')
ref = Path('reference/artifacts/26.1.2/Wurst7-ref-26.1.2/Wurst7-26.1.2/'
           'src/main/java/net/wurstclient/hacks')


def name_of(path):
    t = path.read_text(encoding='utf-8', errors='ignore')
    m = re.search(r'super\(\s*"([^"]+)"', t)
    return m.group(1) if m else path.stem[:-4]


cur_names = {p.stem: name_of(p) for p in cur.glob('*Hack.java')}
ref_names = {p.stem[:-4] for p in ref.glob('*Hack.java')}

buckets = {'Soar': [], 'Meteor': [], 'FO': [], 'Leaves': [],
           'IMG': [], 'Wurst': [], 'Other': []}
for stem, name in sorted(cur_names.items(), key=lambda x: x[1].lower()):
    head = (cur / (stem + '.java')).read_text(encoding='utf-8',
                                             errors='ignore')[:1200]
    if 'Ported from Soar Client' in head:
        buckets['Soar'].append(name)
    elif 'Ported from Meteor Client' in head:
        buckets['Meteor'].append(name)
    elif 'Ported from FO' in head:
        buckets['FO'].append(name)
    elif 'LeavesHack' in head or 'Ported from Leaves' in head:
        buckets['Leaves'].append(name)
    elif 'InvincibleMachineGun' in head:
        buckets['IMG'].append(name)
    elif stem[:-4] in ref_names:
        continue
    elif name in ('AntiKnockback', 'MaceDMG', 'AttributeSwap',
                  'KillauraLegit'):
        buckets['Wurst'].append(name)
    else:
        buckets['Other'].append(name)

for k, v in buckets.items():
    print('##', k, len(v))
    print(', '.join(v))
print('TOTAL', sum(len(v) for v in buckets.values()))
print('CURRENT', len(cur_names), 'REF', len(ref_names))
