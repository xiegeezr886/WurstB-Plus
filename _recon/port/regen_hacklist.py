# -*- coding: utf-8 -*-
"""Rebuild HackList.java from Fabric and re-add surviving loader-only hacks.

Usage:
    python _recon/port/regen_hacklist.py versions/1.21.11 ...
"""
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
DECL_RE = re.compile(
    rb'public final ([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\s*=\s*[^;]{0,300};', re.S)


def to_lf(data):
    return data.replace(b'\r\n', b'\n').replace(b'\r', b'\n')


def main():
    for arg in sys.argv[1:]:
        project = REPO / arg
        props = (project / 'gradle.properties').read_text(encoding='utf-8')
        mc = re.search(r'^minecraft_version\s*=\s*(.+)$', props,
                       re.MULTILINE).group(1).strip()
        source = (REPO / 'fabric' if arg.strip('/') == 'neoforge'
                  else REPO / 'fabric/versions' / mc)
        rel = (project / 'src/main/java/net/wurstclient/hack/HackList.java'
               ).relative_to(REPO).as_posix()
        head = subprocess.run(['git', 'show', 'HEAD:' + rel], cwd=REPO,
                              capture_output=True).stdout
        head = to_lf(head)
        new = to_lf((source / 'src/main/java/net/wurstclient/hack'
                     / 'HackList.java').read_bytes())
        present = {m.group(1) for m in DECL_RE.finditer(new)}
        hacks_dir = project / 'src/main/java/net/wurstclient/hacks'
        add = []
        for m in DECL_RE.finditer(head):
            cls = m.group(1)
            if cls in present:
                continue
            if not (hacks_dir / (cls.decode() + '.java')).exists():
                continue
            add.append(m.group(0))
            present.add(cls)
        if add:
            anchor = b'\tpublic HackList('
            pos = new.find(anchor)
            if pos < 0:
                raise SystemExit('constructor not found in ' + rel)
            new = new[:pos] + b'\n'.join(add) + b'\n\n' + new[pos:]
        out = project / 'src/main/java/net/wurstclient/hack/HackList.java'
        eol = b'\r\n' if b'\r\n' in out.read_bytes() else b'\n'
        out.write_bytes(new.replace(b'\n', eol) if eol == b'\r\n' else new)
        print('%-28s kept %d loader-only hacks' % (arg, len(add)))


if __name__ == '__main__':
    main()
