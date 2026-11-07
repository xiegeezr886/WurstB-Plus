# -*- coding: utf-8 -*-
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs


def snake(name):
    return re.sub(r'[^a-z0-9]+', '_',
                  name.lower().replace("'", '')).strip('_')


def collect(project):
    names = set()
    for p in (project / 'src/main/java/net/wurstclient/hacks').glob('*.java'):
        t = p.read_text(encoding='utf-8', errors='ignore')
        for m in re.finditer(
                r'new\s+\w*Setting(?:<[^>]*>)?\(\s*"([^"]+)"', t):
            names.add(m.group(1))
    return names


def main():
    projects = list(project_dirs())
    if REPO not in projects:
        projects.append(REPO)
    for project in projects:
        zh_path = (project / 'src/main/resources/assets/wurst/translations/'
                   'zh_cn.json')
        if not zh_path.exists():
            continue
        zh = json.loads(zh_path.read_text(encoding='utf-8'))
        names = collect(project)
        missing = sorted(n for n in names
                         if 'setting.name.' + snake(n) not in zh)
        if missing:
            print(project.relative_to(REPO), 'missing settings',
                  len(missing))
            print(' ', ', '.join(missing[:30]))


if __name__ == '__main__':
    main()
