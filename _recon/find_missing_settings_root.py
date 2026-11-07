# -*- coding: utf-8 -*-
import json
import re
from pathlib import Path

root_zh = json.loads(Path(
    'src/main/resources/assets/wurst/translations/zh_cn.json'
).read_text(encoding='utf-8'))
fab_zh = json.loads(Path(
    'fabric/src/main/resources/assets/wurst/translations/zh_cn.json'
).read_text(encoding='utf-8'))


def snake(name):
    return re.sub(r'[^a-z0-9]+', '_',
                  name.lower().replace("'", '')).strip('_')


names = set()
for p in Path('src/main/java/net/wurstclient/hacks').glob('*.java'):
    t = p.read_text(encoding='utf-8', errors='ignore')
    for m in re.finditer(r'new\s+\w*Setting(?:<[^>]*>)?\(\s*"([^"]+)"', t):
        names.add(m.group(1))

missing = sorted(n for n in names
                 if 'setting.name.' + snake(n) not in root_zh)
print('missing', len(missing))
for n in missing:
    k = 'setting.name.' + snake(n)
    print(n, '|| fabric:', fab_zh.get(k))
