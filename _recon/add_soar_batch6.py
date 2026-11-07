# -*- coding: utf-8 -*-
"""Replicate the final Soar Chat/Waypoint hacks to all 66 v1.5 projects."""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

SRC = {
    'fabric/versions/1.21.11': ['ChatHack'],
    'versions/1.21.11': ['WaypointHack'],
}


def main():
    root = REPO.resolve()
    templates = {}
    fields = {}
    for rel, names in SRC.items():
        d = REPO / rel / 'src/main/java/net/wurstclient/hacks'
        list_path = REPO / rel / 'src/main/java/net/wurstclient/hack/HackList.java'
        text = list_path.read_text(encoding='utf-8')
        for n in names:
            p = d / (n + '.java')
            templates[n] = p.read_text(encoding='utf-8')
            m = re.search(r'public final ' + n + r'\s+(\w+)\s*=', text)
            if m:
                fields[n] = m.group(1)
            else:
                print('MISSING FIELD', rel, n)

    stats = {}
    for p in project_dirs():
        if p.resolve() == root:
            continue
        hack_dir = p / 'src/main/java/net/wurstclient/hacks'
        list_path = p / 'src/main/java/net/wurstclient/hack/HackList.java'
        text = list_path.read_text(encoding='utf-8')
        for cls, field in fields.items():
            (hack_dir / (cls + '.java')).write_text(
                templates[cls], encoding='utf-8', newline='\n')
            if field in text:
                stats['exists'] = stats.get('exists', 0) + 1
                continue
            m = re.search(
                r'^([ \t]*)public final \w+ notifierHack\s*=', text, re.M)
            if not m:
                stats['NO ANCHOR'] = stats.get('NO ANCHOR', 0) + 1
                continue
            end = text.find(';', m.end())
            eol = text.find('\n', end) if end != -1 else -1
            if end == -1 or eol == -1:
                stats['NO ANCHOR'] = stats.get('NO ANCHOR', 0) + 1
                continue
            line = '\tpublic final %s %s = new %s();' % (cls, field, cls)
            text = text[:eol + 1] + line + '\n' + text[eol + 1:]
            stats['added'] = stats.get('added', 0) + 1
        list_path.write_text(text, encoding='utf-8', newline='\n')
    print('soar6', stats, 'modules=', len(fields))


if __name__ == '__main__':
    main()
