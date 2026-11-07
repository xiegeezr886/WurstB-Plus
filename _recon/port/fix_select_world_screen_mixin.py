# -*- coding: utf-8 -*-
"""Drop SelectWorldScreenMixin on MC >= 1.21.9.

SelectWorldScreen overrides render(GuiGraphics,IIF) only up to 1.21.8. From
1.21.9 the class no longer declares render, so the @Inject target is missing and
startup crashes. Screen.render still calls renderBackground there, so
ScreenMixin already paints the shared background.

Usage: python _recon/port/fix_select_world_screen_mixin.py [--apply]
"""
import argparse
import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
NAME = 'SelectWorldScreenMixin'


def vt(text):
    return tuple(int(p) for p in text.split('.'))


def projects():
    out = list((REPO / 'fabric/versions').iterdir()) + \
        list((REPO / 'versions').iterdir()) + \
        list((REPO / 'neoforge/versions').iterdir())
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
        if vt(mc) < (1, 21, 9):
            continue
        mixin = (project / 'src/main/java/net/wurstclient/mixin'
                 / (NAME + '.java'))
        cfg = config_path(project)
        removed = []
        if mixin.exists():
            removed.append('class')
            if args.apply:
                mixin.unlink()
        if cfg:
            data = json.loads(cfg.read_text(encoding='utf-8'))
            for key in ('mixins', 'client'):
                lst = data.get(key, [])
                if NAME in lst:
                    data[key] = [e for e in lst if e != NAME]
                    removed.append('config:' + key)
            if removed and args.apply:
                cfg.write_text(json.dumps(data, indent=2, ensure_ascii=False)
                               + '\n', encoding='utf-8', newline='\n')
        if removed:
            print('%-28s mc=%-7s remove %s'
                  % (project.relative_to(REPO).as_posix(), mc,
                     ','.join(removed)))
            changed += 1
    print('projects changed:', changed)


if __name__ == '__main__':
    main()
