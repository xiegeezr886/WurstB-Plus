# -*- coding: utf-8 -*-
"""Resolve the residual copy-vs-new-content conflicts after cross-loader sync.

Most files are simple: the new Fabric side is the shared implementation and the
loader-specific divergence was obsolete code, so the Fabric side wins. A few
files need custom handling:

  * HackList.java     - keep Forge/NeoForge-only hack fields
  * WurstTitleMenu    - keep the loader's mod-list screen + runtime string
  * SelectFileScreen  - keep the 1.21.6 getContentX/Y adaptation
"""
import argparse
import re
import subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

CONFLICT_RE = re.compile(
    rb'<<<<<<< [^\n]*\n(.*?)\|\|\|\|\|\|\| [^\n]*\n(.*?)'
    rb'=======\n(.*?)>>>>>>> [^\n]*\n', re.S)


def to_lf(data):
    return data.replace(b'\r\n', b'\n').replace(b'\r', b'\n')


def from_lf(data, eol):
    return data.replace(b'\n', eol) if eol == b'\r\n' else data


def take_theirs(data):
    return CONFLICT_RE.sub(lambda m: m.group(3), data)


def take_ours(data):
    return CONFLICT_RE.sub(lambda m: m.group(1), data)


def fix_select_file(data):
    def repl(m):
        ours = m.group(1)
        lines = [line for line in ours.split(b'\n')
                 if b'Font tr = minecraft.font;' not in line]
        return b'\n'.join(lines)
    return CONFLICT_RE.sub(repl, data)


def fix_title_menu(data):
    def repl(m):
        ours, base, theirs = m.group(1), m.group(2), m.group(3)
        if b'FabricModListScreen' not in (ours + base + theirs):
            return m.group(0)
        # Forge/NeoForge use their own ModListScreen; the new content still
        # needs these two imports.
        return (b'import net.wurstclient.gui.MainMenuBackground;\n'
                b'import net.wurstclient.hud2.nanovg.NanoVGUI;\n')
    return CONFLICT_RE.sub(repl, data)


DECL_RE = re.compile(
    rb'public final ([A-Za-z0-9_]+) ([A-Za-z0-9_]+)\s*=\s*[^;]{0,300};', re.S)


def fix_hacklist(project, path, source_project):
    rel = path.relative_to(REPO).as_posix()
    p = subprocess.run(['git', 'show', 'HEAD:' + rel], cwd=REPO,
                       capture_output=True)
    head = to_lf(p.stdout) if p.returncode == 0 else b''
    src_path = (REPO / source_project / 'src/main/java/net/wurstclient/hack'
                / 'HackList.java')
    new = to_lf(src_path.read_bytes())

    present = {m.group(1) for m in DECL_RE.finditer(new)}
    hacks_dir = project / 'src/main/java/net/wurstclient/hacks'
    add = []
    for m in DECL_RE.finditer(head):
        cls, name = m.group(1), m.group(2)
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
            raise SystemExit('HackList constructor not found in ' + rel)
        block = b'\n'.join(add) + b'\n\n'
        new = new[:pos] + block + new[pos:]
    return new


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true')
    args = ap.parse_args()

    for project in sorted((REPO / 'versions').iterdir()) + \
            sorted((REPO / 'neoforge/versions').iterdir()):
        if not project.is_dir():
            continue
        rel_proj = project.relative_to(REPO).as_posix()
        props = (project / 'gradle.properties')
        if not props.exists():
            continue
        mc = re.search(r'^minecraft_version\s*=\s*(.+)$',
                       props.read_text(encoding='utf-8'),
                       re.MULTILINE).group(1).strip()
        source_project = 'fabric/versions/' + mc
        src_java = project / 'src/main/java'
        for path in src_java.rglob('*.java'):
            raw = path.read_bytes()
            if b'<<<<<<< ' not in raw:
                continue
            eol = b'\r\n' if b'\r\n' in raw else b'\n'
            data = to_lf(raw)
            name = path.name
            if name == 'HackList.java':
                data = fix_hacklist(project, path, source_project)
            elif name == 'WurstTitleMenu.java':
                data = fix_title_menu(data)
            elif name == 'SelectFileScreen.java':
                data = fix_select_file(data)
            else:
                data = take_theirs(data)
            remaining = data.count(b'<<<<<<< ')
            print('%-28s %-60s conflicts-left=%d'
                  % (rel_proj, path.relative_to(src_java).as_posix(),
                     remaining))
            if args.apply:
                path.write_bytes(from_lf(data, eol))


if __name__ == '__main__':
    main()
