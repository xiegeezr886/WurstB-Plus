# -*- coding: utf-8 -*-
import json
import re
from pathlib import Path

projects = [
    Path('fabric/versions/1.21.11'),
    Path('neoforge/versions/1.21.11'),
    Path('versions/1.21.11'),
    Path('.'),
]

for proj in projects:
    base = proj / 'src/main/resources/assets/wurst/translations'
    zh_path = base / 'zh_cn.json'
    en_path = base / 'en_us.json'
    if not zh_path.exists() or not en_path.exists():
        print(proj, 'missing files')
        continue
    zh = json.loads(zh_path.read_text(encoding='utf-8'))
    en = json.loads(en_path.read_text(encoding='utf-8'))
    same_names = [k for k, v in zh.items()
                  if k.startswith('hack.name.') and en.get(k) == v]
    same_desc = [k for k, v in zh.items()
                 if k.startswith('description.wurst.hack.')
                 and en.get(k) == v]
    print(proj, 'same-as-en names', len(same_names),
          'desc', len(same_desc))
    if same_names:
        print('  names:', ', '.join(k[len('hack.name.'):]
                                   for k in same_names[:20]))
    if same_desc:
        print('  desc:', ', '.join(k[len('description.wurst.hack.'):]
                                  for k in same_desc[:20]))
