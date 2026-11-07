# -*- coding: utf-8 -*-
"""Port the fullported Fabric tree of one MC version onto its Forge/NeoForge sibling.

The Fabric working tree is the source of truth (v1.5.1 content). The Forge /
NeoForge project of the SAME MC version is the target. Old Fabric content is
recovered from git HEAD, so every file gets a real 3-way merge:

    base   = HEAD:fabric/versions/<mc>/<path>
    ours   = target working tree file (loader-adapted)
    theirs = fabric working tree file (new content)

Files that the loader specialization did not touch are copied byte-for-byte;
loader-specific files are merged and reported when they conflict.

Usage:
    python _recon/port/cross_loader_sync.py versions/1.21.9 [--apply]
    python _recon/port/cross_loader_sync.py neoforge/versions/1.21.9 [--apply]

Without --apply it only writes a report under _recon/port/cross-sync/.
"""
import argparse
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]

# Files that exist only in the Fabric project and must never be copied.
# Paths are relative to src/main.
FABRIC_ONLY = {
    'resources/fabric.mod.json',
    'resources/wurstpenguin.mixins.json',
    'resources/wurstpenguin.accesswidener',
    'java/net/wurstclient/WurstInitializer.java',
}

# Mixins that only make sense on Fabric (Yarn-side hooks / Sodium indigo).
FABRIC_ONLY_MIXINS = {
    'IngameHudMixin.java',
    'xray/indigo/AbstractTerrainRenderContextMixin.java',
    'xray/indigo/BlockRenderInfoMixin.java',
}

SKIP_SUFFIXES = ('.disabled', '.class')


def run_git(args):
    p = subprocess.run(['git'] + args, cwd=REPO, capture_output=True)
    return p.returncode, p.stdout


def norm_text(data):
    """Line-ending and BOM insensitive normalisation for text files."""
    if data is None:
        return None
    if b'\x00' in data[:4096]:
        return data
    text = data.decode('utf-8', errors='replace')
    if text.startswith('\ufeff'):
        text = text[1:]
    return text.replace('\r\n', '\n').replace('\r', '\n')


def same(a, b):
    na, nb = norm_text(a), norm_text(b)
    return na is not None and na == nb


def line_count(data):
    if data is None or b'\x00' in data[:4096]:
        return 0
    return to_lf(data).count(b'\n')


def is_rewrite(base, src):
    """True when the new source keeps less than 60% of the old file."""
    base_lines = line_count(base)
    return base_lines > 80 and line_count(src) < 0.6 * base_lines


def git_head(rel):
    code, out = run_git(['show', 'HEAD:' + rel])
    return out if code == 0 else None


def head_blobs(rev_paths):
    """Fetch many `HEAD:<path>` blobs in one `git cat-file --batch` call."""
    if not rev_paths:
        return {}
    payload = ('\n'.join('HEAD:' + p for p in rev_paths) + '\n').encode()
    p = subprocess.run(['git', 'cat-file', '--batch'], cwd=REPO,
                       input=payload, capture_output=True)
    data = p.stdout
    result = {}
    pos = 0
    for rel in rev_paths:
        nl = data.index(b'\n', pos)
        header = data[pos:nl].split()
        pos = nl + 1
        if not header or header[-1] == b'missing':
            result[rel] = None
            continue
        size = int(header[2])
        result[rel] = data[pos:pos + size]
        pos += size + 1
    return result


def to_lf(data):
    if b'\x00' in data[:4096]:
        return data
    data = data.replace(b'\r\n', b'\n').replace(b'\r', b'\n')
    if data.startswith(b'\xef\xbb\xbf'):
        data = data[3:]
    return data


def merge3(base, ours, theirs):
    """Return (merged_bytes, conflict_count).

    Inputs are normalised to LF (the repo checkout is CRLF, the Fabric
    working tree is LF) and the result is converted back to the target's
    line-ending convention.
    """
    eol = b'\r\n' if b'\r\n' in ours else b'\n'
    base, ours, theirs = to_lf(base), to_lf(ours), to_lf(theirs)

    def trim_eof(data):
        # Ignore differences in the number of trailing blank lines.
        return data.rstrip(b'\n') + b'\n' if data.strip(b'\n') else b''

    base, ours, theirs = trim_eof(base), trim_eof(ours), trim_eof(theirs)
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)
        (td / 'base').write_bytes(base)
        (td / 'ours').write_bytes(ours)
        (td / 'theirs').write_bytes(theirs)
        p = subprocess.run(
            ['git', 'merge-file', '-p', '--diff3',
             str(td / 'ours'), str(td / 'base'), str(td / 'theirs')],
            capture_output=True)
        merged = resolve_whitespace_conflicts(p.stdout)
        remaining = merged.count(b'<<<<<<< ')
        if eol == b'\r\n':
            merged = merged.replace(b'\n', b'\r\n')
        return merged, remaining


CONFLICT_RE = re.compile(
    rb'<<<<<<< [^\n]*\n(.*?)\|\|\|\|\|\|\| [^\n]*\n(.*?)'
    rb'=======\n(.*?)>>>>>>> [^\n]*\n', re.S)


def resolve_whitespace_conflicts(data):
    """Auto-resolve conflict hunks whose three sides differ only in whitespace.

    The repo checkout is CRLF while the Fabric tree is LF, so trailing blank
    lines inside otherwise identical hunks would otherwise show up as
    conflicts. The new Fabric side wins.
    """
    def repl(m):
        ours, base, theirs = m.group(1), m.group(2), m.group(3)
        key = lambda s: re.sub(rb'\s+', b'', s)
        if key(ours) == key(base) == key(theirs):
            return theirs
        return m.group(0)

    return CONFLICT_RE.sub(repl, data)


def resolve_title_menu(data):
    """Keep the loader-specific runtime string, switch the draw call to NanoVG.

    The new title menu paints its footer through NanoVGUI; the loader string
    (Forge/NeoForge version) is the only part that must stay target-specific.
    """
    def repl(m):
        ours, base, theirs = m.group(1), m.group(2), m.group(3)
        if b'NanoVGUI.text(runtime' not in theirs:
            return m.group(0)
        if b'NanoVGUI.text(runtime' in ours:
            # already resolved on a previous pass: keep the loader string
            return ours
        if b'graphics.' not in ours:
            return m.group(0)
        lines = ours.split(b'\n')
        cut = next((i for i, line in enumerate(lines)
                    if line.strip().startswith(b'graphics.')), None)
        if cut is None:
            return m.group(0)
        prefix = lines[:cut]
        while prefix and not prefix[-1].strip():
            prefix.pop()
        if not prefix:
            return m.group(0)
        return (b'\n'.join(prefix + [
            b'\t\tNanoVGUI.text(runtime, margin, screenHeight - 18, '
            b'MUTED_TEXT, 9F);']) + b'\n')

    return CONFLICT_RE.sub(repl, data)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('target')
    ap.add_argument('--apply', action='store_true')
    ap.add_argument('--copy-missing', action='store_true',
                    help='copy files absent from the target even if the '
                         'Fabric source did not change since HEAD (lagging '
                         'baseline, e.g. neoforge root)')
    ap.add_argument('--report-dir', default='_recon/port/cross-sync')
    args = ap.parse_args()

    target = (REPO / args.target).resolve()
    props = (target / 'gradle.properties').read_text(encoding='utf-8')
    mc = None
    for line in props.splitlines():
        if line.strip().startswith('minecraft_version='):
            mc = line.split('=', 1)[1].strip()
            break
    if not mc:
        raise SystemExit('no minecraft_version in ' + str(target))

    rel_target = args.target.replace('\\', '/').strip('/')
    if rel_target == 'neoforge':
        src_rel = 'fabric'
    else:
        src_rel = 'fabric/versions/' + mc
    src = REPO / src_rel
    if not src.exists():
        raise SystemExit('no Fabric source for MC ' + mc + ': ' + src_rel)

    loader = 'NeoForge' if rel_target.startswith('neoforge') else 'Forge'
    entries = []
    counts = {
        'copy_new': 0, 'copy_changed': 0, 'rewrite_copy': 0,
        'merge_clean': 0, 'merge_conflict': 0, 'delete': 0,
        'delete_conflict': 0, 'unchanged': 0, 'skipped': 0,
    }

    src_main = src / 'src/main'
    tgt_main = target / 'src/main'
    src_files = sorted(p for p in src_main.rglob('*') if p.is_file())
    src_rels = [p.relative_to(src_main).as_posix() for p in src_files]

    # old Fabric tree: needed both for 3-way merges and deletions
    old_files = []
    code, out = run_git(['ls-tree', '-r', '--name-only', 'HEAD',
                         src_rel + '/src/main'])
    if code == 0:
        for line in out.decode('utf-8').splitlines():
            if line and '/src/main/' in line:
                old_files.append(line.split('/src/main/', 1)[1])

    all_rels = list(dict.fromkeys(src_rels + old_files))
    blobs = head_blobs([src_rel + '/src/main/' + r for r in all_rels])
    head_map = {r: blobs.get(src_rel + '/src/main/' + r) for r in all_rels}

    skip_mixins = FABRIC_ONLY_MIXINS if loader != 'NeoForge' else set()

    for sp in src_files:
        rel = sp.relative_to(src_main).as_posix()
        if rel.startswith('resources/META-INF/') or rel in FABRIC_ONLY:
            counts['skipped'] += 1
            continue
        if rel.endswith(SKIP_SUFFIXES) or 'component.disabled' in rel.split('/'):
            counts['skipped'] += 1
            continue
        if rel.startswith('java/net/wurstclient/mixin/') and \
                rel.split('/', 4)[4] in skip_mixins:
            counts['skipped'] += 1
            continue

        # source may carry its own path under src/main/{java,resources}
        tgt_path = tgt_main / rel
        src_bytes = sp.read_bytes()
        base_bytes = head_map.get(rel)

        if not tgt_path.exists():
            # By default a file that is absent from a same-version sibling is
            # loader-specific. Opt in with --copy-missing for a target whose
            # baseline lags behind Fabric HEAD (neoforge root).
            if base_bytes is not None and same(base_bytes, src_bytes) \
                    and not args.copy_missing:
                counts['unchanged'] += 1
                entries.append(('MISSING_BUT_UNCHANGED', rel, 0))
                continue
            counts['copy_new'] += 1
            entries.append(('COPY_NEW', rel, 0))
            if args.apply:
                tgt_path.parent.mkdir(parents=True, exist_ok=True)
                tgt_path.write_bytes(src_bytes)
            continue

        tgt_bytes = tgt_path.read_bytes()
        if same(tgt_bytes, src_bytes):
            counts['unchanged'] += 1
            continue
        if base_bytes is not None and same(base_bytes, src_bytes):
            counts['unchanged'] += 1
            entries.append(('TARGET_DIVERGED_SOURCE_UNCHANGED', rel, 0))
            continue
        # Wholesale rewrites (most of the old file deleted) are shared,
        # loader-agnostic code: a 3-way merge would stitch old fragments back
        # in, so replace the target file outright.
        if base_bytes is not None and is_rewrite(base_bytes, src_bytes):
            counts['rewrite_copy'] += 1
            entries.append(('REWRITE_COPY', rel, 0))
            if args.apply:
                data = src_bytes
                if b'\r\n' in tgt_bytes:
                    data = to_lf(data).replace(b'\n', b'\r\n')
                tgt_path.write_bytes(data)
            continue
        if base_bytes is not None and same(tgt_bytes, base_bytes):
            counts['copy_changed'] += 1
            entries.append(('COPY_CHANGED', rel, 0))
            if args.apply:
                data = src_bytes
                if b'\r\n' in tgt_bytes:
                    data = to_lf(data).replace(b'\n', b'\r\n')
                tgt_path.write_bytes(data)
            continue

        merged, conflicts = merge3(base_bytes or b'', tgt_bytes, src_bytes)
        if conflicts and rel == \
                'java/net/wurstclient/gui/title/WurstTitleMenu.java':
            merged = resolve_title_menu(to_lf(merged))
            if b'\r\n' in tgt_bytes:
                merged = merged.replace(b'\n', b'\r\n')
            conflicts = merged.count(b'<<<<<<< ')
        if conflicts < 0:
            counts['merge_conflict'] += 1
            entries.append(('MERGE_ERROR', rel, conflicts))
            continue
        if conflicts == 0:
            counts['merge_clean'] += 1
            entries.append(('MERGE_CLEAN', rel, 0))
            if args.apply:
                tgt_path.write_bytes(merged)
        else:
            counts['merge_conflict'] += 1
            entries.append(('MERGE_CONFLICT', rel, conflicts))
            if args.apply:
                tgt_path.write_bytes(merged)

    # deletions: files present in old Fabric but gone from the new tree
    src_set = set(src_rels)
    for rel in old_files:
        if rel in src_set:
            continue
        if rel.startswith('resources/META-INF/') or rel in FABRIC_ONLY:
            continue
        if rel.endswith(SKIP_SUFFIXES):
            continue
        if rel.startswith('java/net/wurstclient/mixin/') and \
                rel.split('/', 4)[4] in skip_mixins:
            continue
        tgt_path = tgt_main / rel
        if not tgt_path.exists():
            continue
        base_bytes = head_map.get(rel)
        tgt_bytes = tgt_path.read_bytes()
        if base_bytes is not None and same(tgt_bytes, base_bytes):
            counts['delete'] += 1
            entries.append(('DELETE', rel, 0))
            if args.apply:
                tgt_path.unlink()
        else:
            counts['delete_conflict'] += 1
            entries.append(('DELETE_CONFLICT', rel, 0))

    report_dir = REPO / args.report_dir
    report_dir.mkdir(parents=True, exist_ok=True)
    name = rel_target.replace('/', '__')
    report = report_dir / (name + '.tsv')
    lines = ['action\tpath\tconflicts']
    for action, rel, conflicts in entries:
        lines.append('%s\t%s\t%s' % (action, rel, conflicts))
    report.write_text('\n'.join(lines) + '\n', encoding='utf-8')

    print('target=%s loader=%s mc=%s source=%s apply=%s'
          % (rel_target, loader, mc, src_rel, args.apply))
    for key in ('copy_new', 'copy_changed', 'rewrite_copy', 'merge_clean',
                'merge_conflict', 'delete', 'delete_conflict',
                'unchanged', 'skipped'):
        print('%-16s %d' % (key, counts[key]))
    print('report:', report.relative_to(REPO))
    if counts['merge_conflict'] or counts['delete_conflict']:
        print('CONFLICT FILES:')
        for action, rel, conflicts in entries:
            if action in ('MERGE_CONFLICT', 'DELETE_CONFLICT', 'MERGE_ERROR'):
                print('  ', action, rel, conflicts)


if __name__ == '__main__':
    main()
