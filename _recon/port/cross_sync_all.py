# -*- coding: utf-8 -*-
"""Run cross_loader_sync.py over every Forge/NeoForge v1.5.1 target."""
import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]


def targets():
    out = []
    for p in sorted((REPO / 'versions').iterdir()):
        if p.is_dir() and (p / 'gradle.properties').exists():
            out.append('versions/' + p.name)
    if (REPO / 'neoforge/gradle.properties').exists():
        out.append('neoforge')
    for p in sorted((REPO / 'neoforge/versions').iterdir()):
        if p.is_dir() and (p / 'gradle.properties').exists():
            out.append('neoforge/versions/' + p.name)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true')
    ap.add_argument('--only', default='')
    args = ap.parse_args()

    summary = []
    for rel in targets():
        if args.only and not re.search(args.only, rel):
            continue
        cmd = [sys.executable, str(REPO / '_recon/port/cross_loader_sync.py'),
               rel]
        if args.apply:
            cmd.append('--apply')
        p = subprocess.run(cmd, cwd=REPO, capture_output=True, text=True)
        text = p.stdout + p.stderr
        if args.apply:
            mcmd = [sys.executable,
                    str(REPO / '_recon/port/merge_mixin_config.py'), rel,
                    '--apply']
            mp = subprocess.run(mcmd, cwd=REPO, capture_output=True, text=True)
            added = re.search(r'added=(\d+)', mp.stdout)
            if added:
                text += '\nmixin_added ' + added.group(1) + '\n'
        values = dict(re.findall(r'^(\w+)\s+(\d+)$', text,
                                 re.MULTILINE))
        conflicts = re.findall(r'^   (MERGE_CONFLICT|DELETE_CONFLICT|MERGE_ERROR) (\S+)',
                               text, re.MULTILINE)
        summary.append((rel, values, conflicts))
        print('%-28s copy=%s rew=%s merge=%s conf=%s del=%s dconf=%s'
              % (rel, values.get('copy_new', '?'),
                 values.get('rewrite_copy', '?'),
                 values.get('merge_clean', '?'),
                 values.get('merge_conflict', '?'),
                 values.get('delete', '?'),
                 values.get('delete_conflict', '?')), flush=True)
        for kind, path in conflicts:
            print('    %-14s %s' % (kind, path), flush=True)

    total_conf = sum(len(c) for _, _, c in summary)
    print('TOTAL targets=%d conflict-files=%d' % (len(summary), total_conf))


if __name__ == '__main__':
    main()
