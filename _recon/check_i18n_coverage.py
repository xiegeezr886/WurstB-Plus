# -*- coding: utf-8 -*-
import json
import re
from pathlib import Path

proj = Path('fabric/versions/1.21.11')
zh = json.loads((proj / 'src/main/resources/assets/wurst/translations/'
                 'zh_cn.json').read_text(encoding='utf-8'))
root = json.loads(Path('src/main/resources/assets/wurst/translations/'
                       'zh_cn.json').read_text(encoding='utf-8'))

names = {}
for p in (proj / 'src/main/java/net/wurstclient/hacks').glob('*Hack.java'):
    t = p.read_text(encoding='utf-8', errors='ignore')
    m = re.search(r'super\(\s*"([^"]+)"', t)
    if m:
        names[m.group(1).lower()] = m.group(1)

mn = [n for n in names if 'hack.name.' + n not in zh]
md = [n for n in names if 'description.wurst.hack.' + n not in zh]
print('missing names', len(mn),
      'covered by root', sum(('hack.name.' + n) in root for n in mn))
print('uncovered names:',
      [names[n] for n in mn if ('hack.name.' + n) not in root])
print('missing desc', len(md),
      'covered by root', sum(('description.wurst.hack.' + n) in root
                             for n in md))
print('desc uncovered:',
      [names[n] for n in md
       if ('description.wurst.hack.' + n) not in root])
