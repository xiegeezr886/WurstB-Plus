# -*- coding: utf-8 -*-
"""Drop the renderDirtBackground @Inject on MC >= 1.20.5.

Screen.renderDirtBackground(GuiGraphics) exists in 1.20.1-1.20.4 only. The
v1.5.1 ScreenMixin still injects into it on every version; Fabric never
validates mixin targets at compile time, so this only surfaces as a crash at
runtime (and as a Forge/NeoForge compile error).

Usage:
    python _recon/port/fix_screen_dirt_injection.py [--apply]
"""
import argparse
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

BLOCK_RE = re.compile(
    rb'\t@Inject\(at = @At\("HEAD"\),\n'
    rb'\t\tmethod = "renderDirtBackground\(Lnet/minecraft/client/gui/'
    rb'GuiGraphics;\)V",\n'
    rb'\t\tcancellable = true\)\n'
    rb'\tprivate void [^\n]*\n'
    rb'(?:\t\t[^\n]*\n)*'
    rb'\t\{.*?\n\t\}\n\n?', re.S)


def version_tuple(text):
    return tuple(int(p) for p in text.split('.'))


def projects():
    out = [REPO / 'fabric'] + list((REPO / 'fabric/versions').iterdir()) + \
        list((REPO / 'versions').iterdir()) + [REPO / 'neoforge'] + \
        list((REPO / 'neoforge/versions').iterdir())
    return [p for p in out if (p / 'gradle.properties').exists()]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true')
    args = ap.parse_args()

    changed = 0
    for project in projects():
        props = (project / 'gradle.properties').read_text(encoding='utf-8')
        mc = re.search(r'^minecraft_version\s*=\s*(.+)$', props,
                       re.MULTILINE).group(1).strip()
        if version_tuple(mc) < (1, 20, 5):
            continue
        path = (project / 'src/main/java/net/wurstclient/mixin/ScreenMixin.java')
        if not path.exists():
            continue
        data = path.read_bytes()
        if b'renderDirtBackground' not in data:
            continue
        eol = b'\r\n' if b'\r\n' in data else b'\n'
        data = data.replace(b'\r\n', b'\n')
        new, count = BLOCK_RE.subn(b'', data)
        if count:
            rel = project.relative_to(REPO).as_posix()
            print('%-28s mc=%s removed=%d' % (rel, mc, count))
            changed += 1
            if args.apply:
                if eol == b'\r\n':
                    new = new.replace(b'\n', b'\r\n')
                path.write_bytes(new)
    print('projects changed:', changed)


if __name__ == '__main__':
    main()
