#!/usr/bin/env python3
"""Inventory Baritone in the v1.5.1 release matrix; never claims gameplay coverage.

Checks declared MC compatibility, API/mixin packaging, legacy flat NeoForge
bundles, and the known broken 26.2 port. Missing releases are failures.
"""
import argparse
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('crash_checks', ROOT / 'scripts/audit-crash-regressions.py')
checks = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checks)


def version(value):
    parts = tuple(map(int, value.split('.')))
    return parts + (0,) * (3 - len(parts))


def admits(declared, target):
    if isinstance(declared, list):
        return any(admits(item, target) for item in declared)
    value = declared.strip()
    if value.endswith('.*'):
        prefix = value[:-2]
        return target == prefix or target.startswith(prefix + '.')
    if value.startswith('~'):
        low = version(value[1:])
        return low <= version(target) < (low[0], low[1] + 1, 0)
    if value.startswith(('>=', '<=', '>', '<')):
        def compare(op, v):
            a, b = version(target), version(v)
            return {'>=': a >= b, '<=': a <= b, '>': a > b, '<': a < b}[op]
        terms = re.findall(r'(>=|<=|>|<)([\d.]+)', value)
        return bool(terms) and all(compare(*term) for term in terms)
    match = re.fullmatch(r'([\[(])\s*([\d.]*)\s*,\s*([\d.]*)\s*([\])])', value)
    if match:
        left, low, high, right = match.groups()
        t = version(target)
        return (not low or (t >= version(low) if left == '[' else t > version(low))) and (
            not high or (t <= version(high) if right == ']' else t < version(high)))
    return version(value.strip('[]')) == version(target)


def inspect_baritone(jar, target, flat=False):
    names = set(jar.namelist())
    errors = []
    for entry in ('baritone/api/BaritoneAPI.class', 'baritone/api/IBaritone.class',
                  'baritone/api/pathing/goals/GoalXZ.class', 'mixins.baritone.json'):
        if entry not in names:
            errors.append('missing ' + entry)
    if 'mixins.baritone.json' in names:
        config = json.loads(jar.read('mixins.baritone.json'))
        for name in config.get('client', []) + config.get('mixins', []):
            path = (config['package'] + '.' + name).replace('.', '/') + '.class'
            if path not in names:
                errors.append('missing registered mixin ' + path)
    declared = None
    if not flat:
        if 'fabric.mod.json' in names:
            declared = json.loads(jar.read('fabric.mod.json')).get('depends', {}).get('minecraft')
        else:
            toml = '\n'.join(jar.read(name).decode() for name in (
                'META-INF/neoforge.mods.toml', 'META-INF/mods.toml') if name in names)
            declared = re.findall(r'modId\s*=\s*"minecraft"[\s\S]{0,200}?versionRange\s*=\s*"([^"]+)"', toml)
        if not declared or not admits(declared, target):
            errors.append(f'Minecraft constraint {declared!r} excludes {target}')
    return declared, errors


def audit(directory):
    rows = []
    for project in checks.projects(ROOT):
        props = dict(re.findall(r'^([\w.]+)=(.*)$', (project / 'gradle.properties').read_text('utf-8'), re.M))
        mc = props['minecraft_version'].strip()
        rel = project.relative_to(ROOT).as_posix()
        loader = 'Fabric' if rel.startswith('fabric') else 'NeoForge' if rel.startswith('neoforge') else 'Forge'
        path = directory / f'WurstB+.Plus-1.5.1-{loader}-{mc}.jar'
        row = dict(project=rel, loader=loader, minecraft=mc, jar=str(path),
                   gameplay='NOT_TESTED', errors=[])
        rows.append(row)
        if not path.exists():
            row['errors'].append('release jar missing')
            continue
        with zipfile.ZipFile(path) as outer:
            row['errors'].extend(checks.check_archive(outer))
            entries = [n for n in outer.namelist() if n.startswith('META-INF/') and n.endswith('.jar') and 'baritone' in n]
            row['packaging'] = 'nested' if entries else 'flat'
            if len(entries) > 1:
                row['errors'].append('multiple embedded Baritone jars')
            if entries:
                data = outer.read(entries[0])
                row['baritone'] = entries[0]
                row['baritone_sha256'] = hashlib.sha256(data).hexdigest()
                with zipfile.ZipFile(io.BytesIO(data)) as inner:
                    row['declared_minecraft'], errors = inspect_baritone(inner, mc)
            else:
                if loader != 'NeoForge' or mc not in checks.FLAT_NEO:
                    row['errors'].append('unexpected flat/missing Baritone packaging')
                _, errors = inspect_baritone(outer, mc, flat=True)
            row['errors'].extend(errors)
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', type=Path, default=ROOT / 'build/release-v1.5')
    parser.add_argument('--output', type=Path, default=ROOT / 'report/baritone-matrix-v1.5.1.json')
    args = parser.parse_args()
    rows = audit(args.directory)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    for row in rows:
        if row['errors']:
            print(row['project'] + ': ' + '; '.join(row['errors']))
    failed = sum(bool(row['errors']) for row in rows)
    print(f'Packaging/metadata: {len(rows)-failed}/{len(rows)} pass. Gameplay: NOT TESTED. Report: {args.output}')
    return bool(failed)


if __name__ == '__main__':
    raise SystemExit(main())
