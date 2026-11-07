# -*- coding: utf-8 -*-
"""Remove mixin-config entries whose class file does not exist.

The v1.5.1 fullport renamed/deleted several mixin classes but left their
entries in some wurstpenguin.mixins.json files. Mixin treats a missing entry
as a load error, so these are real runtime bugs even though compileJava is
happy.

Usage:
    python _recon/port/clean_stale_mixins.py            # report only
    python _recon/port/clean_stale_mixins.py --apply
"""
import argparse
import json
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]


def projects():
    out = []
    if (REPO / 'fabric').is_dir():
        out.append(REPO / 'fabric')
    out += sorted(p for p in (REPO / 'fabric/versions').iterdir() if p.is_dir())
    out += sorted(p for p in (REPO / 'versions').iterdir() if p.is_dir())
    if (REPO / 'neoforge').is_dir():
        out.append(REPO / 'neoforge')
    out += sorted(p for p in (REPO / 'neoforge/versions').iterdir()
                  if p.is_dir())
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true')
    args = ap.parse_args()

    changed = 0
    for project in projects():
        cfg_name = ('wurstpenguin.mixins.json'
                    if project.name.startswith('fabric') or
                    (project / 'src/main/resources/fabric.mod.json').exists()
                    else 'wurst.mixins.json')
        cfg = project / 'src/main/resources' / cfg_name
        if not cfg.exists():
            cfg = project / 'src/main/resources/wurst.mixins.json'
        if not cfg.exists():
            continue
        root = project / 'src/main/java/net/wurstclient/mixin'
        data = json.loads(cfg.read_text(encoding='utf-8'))
        removed = []
        for key in ('mixins', 'client'):
            kept = []
            for entry in data.get(key, []):
                cls = root / (entry.replace('.', '/') + '.java')
                if cls.exists():
                    kept.append(entry)
                else:
                    removed.append(key + ':' + entry)
            data[key] = kept
        if not removed:
            continue
        changed += 1
        rel = project.relative_to(REPO).as_posix()
        print(rel, 'removes', len(removed))
        for item in removed:
            print('  -', item)
        if args.apply:
            cfg.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n',
                           encoding='utf-8', newline='\n')
    print('projects with stale entries:', changed)


if __name__ == '__main__':
    main()
