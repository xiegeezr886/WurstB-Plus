# -*- coding: utf-8 -*-
import subprocess
import re
import pathlib
import json

r = pathlib.Path(r'C:\Users\ui863\Documents\trae_projects\VAP')
out = subprocess.run(['git', '-C', str(r), 'diff-tree', '--no-commit-id',
                      '--name-status', '-r', '8c034224'],
                     capture_output=True, text=True, encoding='utf-8',
                     errors='replace').stdout.splitlines()
classes = sorted({l.split('/')[-1][:-5] for l in out
                  if l.startswith('A') and '/hacks/' in l
                  and l.strip().endswith('Hack.java')})
files = {}
for p in r.rglob('*Hack.java'):
    if 'build' in p.parts or 'reference' in p.parts:
        continue
    if p.stem in classes and p.stem not in files:
        files[p.stem] = p
names = set()
for c, p in files.items():
    t = p.read_text(encoding='utf-8', errors='ignore')
    for m in re.finditer(r'new\s+\w*Setting(?:<[^>]*>)?\(\s*"([^"]+)"', t):
        names.add(m.group(1))


def snake(n):
    n = n.lower().replace("'", '')
    n = re.sub(r'[^a-z0-9]+', '_', n)
    return n.strip('_')


zh = json.loads((r / 'fabric/versions/1.21.11/src/main/resources/assets/wurst/'
                 'translations/zh_cn.json').read_text(encoding='utf-8'))
missing = sorted(n for n in names if ('setting.name.' + snake(n)) not in zh)
print('unique', len(names), 'already translated', len(names) - len(missing),
      'missing', len(missing))
for n in missing:
    print(n)
