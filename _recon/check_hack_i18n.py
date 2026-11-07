# -*- coding: utf-8 -*-
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs


def names_for(project):
    hacks = project / 'src/main/java/net/wurstclient/hacks'
    out = {}
    for p in hacks.glob('*Hack.java'):
        t = p.read_text(encoding='utf-8', errors='ignore')
        for m in re.finditer(r'super\(\s*"([^"]+)"', t):
            n = m.group(1)
            out[n.lower()] = n
            break
    return out


def main():
    projects = list(project_dirs())
    if REPO not in projects:
        projects.append(REPO)
    total_missing_names = total_missing_desc = 0
    for project in projects:
        zh = (project / 'src/main/resources/assets/wurst/translations/'
              'zh_cn.json')
        if not zh.exists():
            print(project, 'NO ZH')
            continue
        data = json.loads(zh.read_text(encoding='utf-8'))
        names = names_for(project)
        mn = sorted(n for n in names if 'hack.name.' + n not in data)
        md = sorted(n for n in names
                    if 'description.wurst.hack.' + n not in data)
        total_missing_names += len(mn)
        total_missing_desc += len(md)
        if mn or md:
            print(project.relative_to(REPO), 'names', len(mn),
                  'desc', len(md))
            if mn:
                print('  missing names:', ', '.join(names[n] for n in mn[:20]))
            if md:
                print('  missing desc:', ', '.join(names[n] for n in md[:20]))
    print('TOTAL missing names', total_missing_names,
          'missing descriptions', total_missing_desc)


if __name__ == '__main__':
    main()
