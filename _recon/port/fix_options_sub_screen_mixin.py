# -*- coding: utf-8 -*-
"""Fix OptionsSubScreenMixin across MC versions.

Facts (verified with javap):
  * 1.20.1: OptionsSubScreen.basicListRender exists and calls
    renderBackground(GuiGraphics) directly -> mixin is valid.
  * 1.20.2: basicListRender exists but calls Screen.render, so the @Redirect
    target is absent -> keep the @Inject, drop the @Redirect.
  * 1.20.3+: basicListRender does not exist at all (the class moved to
    screens.options in 1.21) -> unregister and delete the mixin.

The background replacement is already handled by ScreenMixin, so dropping the
redirect does not change behaviour.

Usage: python _recon/port/fix_options_sub_screen_mixin.py [--apply]
"""
import argparse
import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

REDIRECT_RE = re.compile(
    rb'\t@Redirect\(at = @At\(value = "INVOKE",\n'
    rb'\t\ttarget = "Lnet/minecraft/client/gui/screens/OptionsSubScreen;'
    rb'renderBackground\(Lnet/minecraft/client/gui/GuiGraphics;\)V"\),\n'
    rb'\t\tmethod = "basicListRender"\)\n'
    rb'\tprivate void [^\n]*\n'
    rb'\t\tGuiGraphics graphics\)\n'
    rb'\t\{.*?\n\t\}\n\n?', re.S)


def vt(text):
    return tuple(int(p) for p in text.split('.'))


def projects():
    out = [REPO / 'fabric']
    out += list((REPO / 'fabric/versions').iterdir())
    out += list((REPO / 'versions').iterdir())
    out += [REPO / 'neoforge']
    out += list((REPO / 'neoforge/versions').iterdir())
    return [p for p in out if (p / 'gradle.properties').exists()]


def config_path(project):
    p = project / 'src/main/resources/wurstpenguin.mixins.json'
    if p.exists():
        return p
    p = project / 'src/main/resources/wurst.mixins.json'
    return p if p.exists() else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true')
    args = ap.parse_args()
    changed = 0

    for project in projects():
        props = (project / 'gradle.properties').read_text(encoding='utf-8')
        mc = re.search(r'^minecraft_version\s*=\s*(.+)$', props,
                       re.MULTILINE).group(1).strip()
        ver = vt(mc)
        mixin = (project / 'src/main/java/net/wurstclient/mixin'
                 / 'OptionsSubScreenMixin.java')
        cfg = config_path(project)
        rel = project.relative_to(REPO).as_posix()

        if ver == (1, 20, 2) and mixin.exists():
            raw = mixin.read_bytes()
            eol = b'\r\n' if b'\r\n' in raw else b'\n'
            data = raw.replace(b'\r\n', b'\n')
            new, n = REDIRECT_RE.subn(b'', data)
            if n:
                print('%-28s keep @Inject, drop @Redirect' % rel)
                changed += 1
                if args.apply:
                    if eol == b'\r\n':
                        new = new.replace(b'\n', b'\r\n')
                    mixin.write_bytes(new)
            continue

        if ver >= (1, 20, 3):
            removed = []
            if mixin.exists():
                removed.append('class')
                if args.apply:
                    mixin.unlink()
            if cfg:
                data = json.loads(cfg.read_text(encoding='utf-8'))
                for key in ('mixins', 'client'):
                    lst = data.get(key, [])
                    if 'OptionsSubScreenMixin' in lst:
                        data[key] = [e for e in lst
                                     if e != 'OptionsSubScreenMixin']
                        removed.append('config:' + key)
                if removed and args.apply:
                    cfg.write_text(
                        json.dumps(data, indent=2, ensure_ascii=False) + '\n',
                        encoding='utf-8', newline='\n')
            if removed:
                print('%-28s mc=%-7s remove %s' % (rel, mc, ','.join(removed)))
                changed += 1

    print('projects changed:', changed)


if __name__ == '__main__':
    main()
