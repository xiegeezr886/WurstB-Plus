"""Merge reviewed port changes into the explicitly authorized Forge1.20.1 root.

The eight child projects are excluded. Default mode only prepares a preview.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
from sync_four_client_batch import ROOT, PLAN

TARGET = ROOT.parent / 'VAP-v1.5.0-1.20.1'
OUT = PLAN / 'external-v151'
SOURCE = 'neoforge'


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def normalized(raw):
    return raw.decode('utf-8-sig').replace('\r\n', '\n')


def source_outputs():
    outputs = {}
    for file in sorted(PLAN.glob('batch*-outputs.json'), key=lambda f: int(re.search(r'batch(\d+)', f.name)[1])):
        for path, sha in json.loads(file.read_text(encoding='utf-8')).items():
            if path.startswith(SOURCE + '/src/'):
                outputs[path.removeprefix(SOURCE + '/')] = (path, sha)
    return outputs


def merge_json(ours, base, theirs):
    current, before, incoming = [json.loads(s) for s in [ours, base, theirs]]
    if 'client' in incoming and 'package' in incoming:
        assert incoming['package'] == current['package'] == 'net.wurstclient.mixin'
        assert all(before.get(k) == v for k, v in incoming.items() if k != 'client')
        assert not set(before['client']) - set(incoming['client'])
        additions = [s for s in incoming['client'] if s not in before['client'] and s not in current['client']]
        marker = '"client": ['
        assert ours.count(marker) == 1
        result = ours.replace(marker, marker + ''.join('\n    ' + json.dumps(s) + ',' for s in additions))
        assert all(s in json.loads(result)['client'] for s in incoming['client'])
        return result
    assert not set(before) - set(incoming), 'Translation removals require review'
    additions = {}
    for key, value in incoming.items():
        if value == before.get(key) or value == current.get(key):
            continue
        assert key not in current or current[key] == before.get(key), ('Translation conflict', key)
        if key in current:
            pattern = re.compile(r'("' + re.escape(key) + r'"\s*:\s*)"(?:[^"\\]|\\.)*"')
            ours, count = pattern.subn(lambda m: m[1] + json.dumps(value, ensure_ascii=False), ours)
            assert count == 1
        else:
            additions[key] = value
    insert = ''.join('\n  ' + json.dumps(k) + ': ' + json.dumps(v, ensure_ascii=False) + ',' for k, v in additions.items())
    return ours.replace('{', '{' + insert, 1)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    assert (TARGET / 'gradle.properties').is_file()
    props = (TARGET / 'gradle.properties').read_text(encoding='utf-8')
    assert 'minecraft_version=1.20.1' in props and 'mod_version=v1.5.1-Forge-1.20.1' in props
    OUT.mkdir(exist_ok=True)
    existing_manifest = OUT / 'root-outputs.json'
    previous = json.loads(existing_manifest.read_text(encoding='utf-8')) if existing_manifest.exists() else {}
    planned, records = {}, []
    for relative, (source_path, expected_sha) in source_outputs().items():
        assert relative.startswith('src/main/'), relative
        target = TARGET / relative
        source_raw = (ROOT / source_path).read_bytes()
        assert digest(source_raw) == expected_sha, ('Changed source', source_path)
        ours_raw = target.read_bytes() if target.exists() else None
        ours = normalized(ours_raw) if ours_raw is not None else None
        theirs = normalized(source_raw)
        saved_source = OUT / 'source-applied' / relative
        backups = [p for p in [PLAN / 'before' / source_path,
                   *[PLAN / f'batch{i}/before' / source_path for i in range(1, 100)]] if p.exists()]
        if saved_source.exists():
            base = normalized(saved_source.read_bytes())
        else:
            base = normalized(backups[0].read_bytes()) if backups else None
        if ours is None:
            merged, method = theirs, 'new'
        elif ours == theirs:
            merged, method = ours, 'already-identical'
        elif base is None:
            raise AssertionError(('Existing file has no reviewed base', target))
        elif ours == base:
            merged, method = theirs, 'base-matches'
        elif relative.endswith('.json'):
            merged, method = merge_json(ours, base, theirs), 'semantic-json-merge'
        else:
            temp = OUT / 'root-merge-temp'
            temp.mkdir(exist_ok=True)
            for name, value in [('ours', ours), ('base', base), ('theirs', theirs)]:
                (temp / name).write_bytes(value.encode('utf-8'))
            run = subprocess.run(['git', 'merge-file', '-p', '--diff3', str(temp / 'ours'), str(temp / 'base'), str(temp / 'theirs')], capture_output=True)
            assert run.returncode == 0, ('Manual merge required', target, run.stderr.decode('utf-8', 'replace'))
            merged, method = normalized(run.stdout), 'three-way-merge'
        assert not re.search(r'^(<<<<<<<|=======|>>>>>>>)', merged, re.M), target
        if relative.endswith('.json'):
            json.loads(merged)
        # Keep each existing file's newline convention and BOM.
        newline = '\r\n' if ours_raw and b'\r\n' in ours_raw else '\n'
        raw = merged.replace('\n', newline).encode('utf-8')
        if ours_raw and ours_raw.startswith(b'\xef\xbb\xbf'):
            raw = b'\xef\xbb\xbf' + raw
        planned[relative] = (raw, ours_raw, source_raw)
        preview = OUT / 'root-candidate' / relative
        preview.parent.mkdir(parents=True, exist_ok=True)
        preview.write_bytes(raw)
        records.append({'file': relative, 'source': source_path, 'source_sha256': expected_sha,
                        'before_sha256': digest(ours_raw) if ours_raw is not None else None,
                        'after_sha256': digest(raw), 'method': method})
    (OUT / 'root-merge-review.json').write_text(json.dumps(records, indent=2), encoding='utf-8')
    if args.apply:
        # Verify the entire batch before writing its first file.
        for relative, (_, observed, _) in planned.items():
            target = TARGET / relative
            assert (target.read_bytes() if target.exists() else None) == observed, ('Concurrent target edit', target)
        for relative, (raw, before, source_raw) in planned.items():
            target = TARGET / relative
            backup = OUT / 'root-before' / relative
            if before is not None and not backup.exists():
                backup.parent.mkdir(parents=True, exist_ok=True)
                backup.write_bytes(before)
            target.parent.mkdir(parents=True, exist_ok=True)
            if before != raw:
                target.write_bytes(raw)
            saved = OUT / 'source-applied' / relative
            saved.parent.mkdir(parents=True, exist_ok=True)
            saved.write_bytes(source_raw)
            previous[relative] = digest(raw)
        existing_manifest.write_text(json.dumps(previous, indent=2), encoding='utf-8')
    print(('Applied' if args.apply else 'Previewed') + f' {len(records)} files in Forge1.20.1 root only; child projects excluded.')


if __name__ == '__main__':
    main()
