# -*- coding: utf-8 -*-
"""Delete hack classes that the v1.5.1 HUD rewrite removed.

Two sources:
  * DELETE_CONFLICT entries in the cross-sync reports (target file diverged
    from old Fabric but the feature is gone in the new tree).
  * For the lagging `neoforge` root: every hacks/*.java that does not exist in
    the Fabric source tree, plus the orphaned hud2/HackHudElement.java.

Usage:
    python _recon/port/delete_obsolete_hacks.py [--apply]
"""
import argparse
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]


def project_name(rel):
    return rel.replace('/', '__')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true')
    args = ap.parse_args()

    reports = REPO / '_recon/port/cross-sync'
    removed = 0
    for report in sorted(reports.glob('*.tsv')):
        project = REPO / report.stem.replace('__', '/')
        if not project.exists():
            continue
        for line in report.read_text(encoding='utf-8').splitlines():
            parts = line.split('\t')
            if len(parts) >= 2 and parts[0] == 'DELETE_CONFLICT':
                path = project / 'src/main' / parts[1]
                if path.exists():
                    print('delete', path.relative_to(REPO).as_posix())
                    if args.apply:
                        path.unlink()
                    removed += 1

    # lagging neoforge root
    neoforge = REPO / 'neoforge'
    fabric = REPO / 'fabric'
    if (neoforge / 'src/main/java').exists():
        src_hacks = {p.name for p in
                     (fabric / 'src/main/java/net/wurstclient/hacks').glob('*.java')}
        for path in (neoforge / 'src/main/java/net/wurstclient/hacks').glob('*.java'):
            if path.name not in src_hacks:
                print('delete', path.relative_to(REPO).as_posix())
                if args.apply:
                    path.unlink()
                removed += 1
        orphan = (neoforge / 'src/main/java/net/wurstclient/hud2'
                  / 'HackHudElement.java')
        if orphan.exists():
            print('delete', orphan.relative_to(REPO).as_posix())
            if args.apply:
                orphan.unlink()
            removed += 1

    print('candidates:', removed)


if __name__ == '__main__':
    main()
