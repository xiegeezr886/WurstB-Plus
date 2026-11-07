# -*- coding: utf-8 -*-
"""Copy current Fabric content over target files that match an OLD revision.

Pairs with audit_stale_content.py. Only files whose target bytes equal a
historical Fabric revision are touched, so genuine loader adaptations are
never overwritten.

Usage: python _recon/port/update_stale_content.py [--apply]
"""
import argparse
import os
import re
import subprocess
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
REVS = 8


def norm(data):
    return (data.replace(b'\r\n', b'\n').replace(b'\r', b'\n')
            .lstrip(b'\xef\xbb\xbf'))


def git(*args):
    return subprocess.run(['git'] + list(args), cwd=REPO,
                          capture_output=True)


def batch_blobs(specs):
    if not specs:
        return {}
    payload = ('\n'.join(specs) + '\n').encode()
    p = subprocess.run(['git', 'cat-file', '--batch'], cwd=REPO,
                       input=payload, capture_output=True)
    data = p.stdout
    out = {}
    pos = 0
    for spec in specs:
        nl = data.index(b'\n', pos)
        header = data[pos:nl].split()
        pos = nl + 1
        if not header or header[-1] == b'missing':
            out[spec] = None
            continue
        size = int(header[2])
        out[spec] = data[pos:pos + size]
        pos += size + 1
    return out


def targets():
    out = []
    for base in ('versions', 'neoforge/versions'):
        for d in sorted((REPO / base).iterdir()):
            if (d / 'gradle.properties').exists():
                out.append(d)
    return out


def stale_for(project):
    props = (project / 'gradle.properties').read_text(encoding='utf-8')
    mc = re.search(r'^minecraft_version\s*=\s*(.+)$', props,
                   re.MULTILINE).group(1).strip()
    src = REPO / 'fabric/versions' / mc / 'src/main'
    dst = project / 'src/main'
    if not src.exists():
        return mc, []
    diffs = []
    for root, _, files in os.walk(src):
        for name in files:
            sp = Path(root) / name
            rel = sp.relative_to(src).as_posix()
            dp = dst / rel
            if not dp.exists():
                continue
            if norm(sp.read_bytes()) == norm(dp.read_bytes()):
                continue
            diffs.append(rel)

    specs = []
    per_path = {}
    for rel in diffs:
        fab = 'fabric/versions/%s/src/main/%s' % (mc, rel)
        hashes = [h for h in git('log', '--format=%H', '-n', str(REVS),
                                 '--', fab).stdout.decode().split() if h]
        per_path[rel] = hashes
        specs += ['%s:%s' % (h, fab) for h in hashes]
    blobs = batch_blobs(specs)
    stale = []
    for rel in diffs:
        hist = set()
        for h in per_path[rel]:
            b = blobs.get('%s:fabric/versions/%s/src/main/%s' % (h, mc, rel))
            if b is not None:
                hist.add(norm(b))
        if norm((dst / rel).read_bytes()) in hist:
            stale.append(rel)
    return mc, stale


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--apply', action='store_true')
    args = ap.parse_args()
    total = 0
    for project in targets():
        mc, stale = stale_for(project)
        if not stale:
            continue
        rel_project = project.relative_to(REPO).as_posix()
        for rel in stale:
            src = REPO / 'fabric/versions' / mc / 'src/main' / rel
            dst = project / 'src/main' / rel
            print('update %-26s %s' % (rel_project, rel))
            if args.apply:
                raw = dst.read_bytes()
                data = src.read_bytes()
                if b'\r\n' in raw:
                    data = data.replace(b'\r\n', b'\n').replace(
                        b'\n', b'\r\n')
                dst.write_bytes(data)
            total += 1
    print('stale files:', total)


if __name__ == '__main__':
    main()
