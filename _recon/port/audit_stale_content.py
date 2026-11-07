# -*- coding: utf-8 -*-
"""Find target files whose content still matches an OLD Fabric revision.

Our sync merged anything Fabric changed relative to Fabric HEAD. A file whose
Fabric HEAD content already equalled the working tree was skipped when the
target diverged (treated as a loader adaptation). Some of those may actually be
stale content. This audit checks whether the target bytes match any of the
last N Fabric revisions of the same path; a match is proof of staleness.

Usage: python _recon/port/audit_stale_content.py
"""
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


def main():
    stale = []
    for project in targets():
        props = (project / 'gradle.properties').read_text(encoding='utf-8')
        mc = re.search(r'^minecraft_version\s*=\s*(.+)$', props,
                       re.MULTILINE).group(1).strip()
        src = REPO / 'fabric/versions' / mc / 'src/main'
        dst = project / 'src/main'
        if not src.exists():
            continue
        rel_project = project.relative_to(REPO).as_posix()
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

        # collect last revisions for each differing Fabric path
        specs = []
        per_path = {}
        for rel in diffs:
            fab = 'fabric/versions/%s/src/main/%s' % (mc, rel)
            revs = git('log', '--format=%H', '-n', str(REVS), '--', fab)
            hashes = [h for h in revs.stdout.decode().split() if h]
            per_path[rel] = hashes
            for h in hashes:
                specs.append('%s:%s' % (h, fab))
        blobs = batch_blobs(specs)
        hist = {}
        for rel, hashes in per_path.items():
            vals = set()
            for h in hashes:
                blob = blobs.get('%s:fabric/versions/%s/src/main/%s'
                                 % (h, mc, rel))
                if blob is not None:
                    vals.add(norm(blob))
            hist[rel] = vals

        for rel in diffs:
            target = norm((dst / rel).read_bytes())
            if target in hist.get(rel, set()):
                stale.append((rel_project, mc, rel))
                print('STALE  %-28s mc=%-7s %s' % (rel_project, mc, rel))

    print('total stale files:', len(stale))


if __name__ == '__main__':
    main()
