# -*- coding: utf-8 -*-
"""Flatten the v1.5.1 content into the external VAP-v1.5.0-1.20.1 Forge tree.

The target is a v1.5.0 Forge 1.20.1 project with substantial local work
(Combat* utilities, extra hacks, Chinese translations). Only add/copy content
from the v1.5.1 NeoForge-compatible donor; never overwrite a locally modified
or untracked target file.

Usage: python _recon/port/flatten_to_v150_forge.py [--apply]
"""
import argparse
import os
import subprocess
from pathlib import Path

DONOR = Path(r'C:\Users\ui863\Documents\trae_projects\VAP\neoforge\src\main')
TARGET = Path(r'C:\Users\ui863\Documents\trae_projects\VAP-v1.5.0-1.20.1')
TROOT = TARGET / 'src' / 'main'

SKIP_PREFIXES = (
    'resources/META-INF/',
    'resources/assets/examplemod/',
    'resources/README.md',
)


def norm(data):
    return (data.replace(b'\r\n', b'\n').replace(b'\r', b'\n')
            .lstrip(b'\xef\xbb\xbf'))


def dirty_paths():
    p = subprocess.run(
        ['git', '-C', str(TARGET), 'status', '--porcelain', '-z',
         '--untracked-files=all', '--', 'src'],
        capture_output=True)
    dirty = set()
    for entry in p.stdout.split(b'\0'):
        if len(entry) < 4:
            continue
        text = entry.decode('utf-8', errors='replace')
        path = text[3:]
        if ' -> ' in path:
            path = path.split(' -> ', 1)[1]
        dirty.add(path.replace('\\', '/'))
    return dirty


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true')
    args = ap.parse_args()
    dirty = dirty_paths()
    copied = overwritten = kept = 0
    for root, _, files in os.walk(DONOR):
        for name in files:
            sp = Path(root) / name
            rel = sp.relative_to(DONOR).as_posix()
            if rel.startswith(SKIP_PREFIXES):
                continue
            if any(part.endswith('.disabled') for part in rel.split('/')):
                continue
            tp = TROOT / rel
            trel = 'src/main/' + rel
            data = sp.read_bytes()
            if not tp.exists():
                action = 'ADD'
                copied += 1
            elif trel in dirty:
                action = 'KEEP'
                kept += 1
            elif norm(tp.read_bytes()) == norm(data):
                action = 'SAME'
                kept += 1
            else:
                action = 'OVERWRITE'
                overwritten += 1
            if action in ('ADD', 'OVERWRITE'):
                print('%-9s %s' % (action, rel))
                if args.apply:
                    tp.parent.mkdir(parents=True, exist_ok=True)
                    tp.write_bytes(data)
            else:
                print('%-9s %s' % (action, rel))
    print('copy_new=%d overwrite=%d kept=%d' % (copied, overwritten, kept))


if __name__ == '__main__':
    main()
