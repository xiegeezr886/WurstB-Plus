# -*- coding: utf-8 -*-
"""Adapt ScreenMixin to the 26.x screen API.

26.x removed Screen.renderDirtBackground and renamed
render(GuiGraphicsExtractor,IIF) to extractRenderState(GuiGraphicsExtractor,IIF).

Usage: python _recon/port/fix_screen_mixin_26x.py [--apply]
"""
import argparse
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

DIRT_RE = re.compile(
    rb'\t@Inject\(at = @At\("HEAD"\),\n'
    rb'\t\tmethod = "renderDirtBackground\(Lnet/minecraft/client/gui/'
    rb'GuiGraphicsExtractor;\)V",\n'
    rb'\t\tcancellable = true\)\n'
    rb'\tprivate void wurst\$replaceDirtBackground\('
    rb'GuiGraphicsExtractor graphics,\n'
    rb'\t\tCallbackInfo ci\)\n'
    rb'\t\{.*?\n\t\}\n\n?', re.S)

OLD_RENDER = b'render(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V'
NEW_RENDER = (b'extractRenderState(Lnet/minecraft/client/gui/'
              b'GuiGraphicsExtractor;IIF)V')


def vt(text):
    return tuple(int(p) for p in text.split('.'))


def projects():
    out = list((REPO / 'fabric/versions').iterdir()) + \
        list((REPO / 'versions').iterdir()) + \
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
        if vt(mc) < (26, 1):
            continue
        path = project / 'src/main/java/net/wurstclient/mixin/ScreenMixin.java'
        if not path.exists():
            continue
        raw = path.read_bytes()
        eol = b'\r\n' if b'\r\n' in raw else b'\n'
        data = raw.replace(b'\r\n', b'\n')
        dirt = len(DIRT_RE.findall(data))
        renders = data.count(OLD_RENDER)
        if not dirt and not renders:
            continue
        data = DIRT_RE.sub(b'', data)
        data = data.replace(OLD_RENDER, NEW_RENDER)
        print('%-28s mc=%-7s dirt=%d render->extract=%d'
              % (project.relative_to(REPO).as_posix(), mc, dirt, renders))
        changed += 1
        if args.apply:
            if eol == b'\r\n':
                data = data.replace(b'\n', b'\r\n')
            path.write_bytes(data)
    print('projects changed:', changed)


if __name__ == '__main__':
    main()
