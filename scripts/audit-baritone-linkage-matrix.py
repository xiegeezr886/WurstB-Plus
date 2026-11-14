#!/usr/bin/env python3
"""Audit every v1.5.1 artifact's embedded Baritone against its Minecraft jar.

The release matrix mixes three mapping flavours:

* Fabric jars embed Baritone with intermediary names (`net/minecraft/class_310`).
* Forge 1.20.2-1.20.4 embed SRG member names (`m_12345_`).
* Forge 1.20.6+ and all NeoForge jars embed Mojang names.

For each artifact this finds a matching mapped Minecraft jar from the local
Gradle caches, runs the same reference check as audit-baritone-linkage.py and
records the result. Artifacts whose mapping flavour has no usable jar available
are reported as skipped instead of silently passing.

Usage:
    python scripts/audit-baritone-linkage-matrix.py
    python scripts/audit-baritone-linkage-matrix.py --output report/linkage.json
"""
import argparse
import glob
import importlib.util
import os
import io
import json
import re
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location(
    'linkage', ROOT / 'scripts/audit-baritone-linkage.py')
linkage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(linkage)

JDK = ROOT / '.dsh-tmp/toolchains/zulu25.38.109-ca-jdk25.0.5.0.3-win_x64'

fabric_spec = importlib.util.spec_from_file_location(
    'fabric_linkage', ROOT / 'scripts/audit-fabric-linkage.py')
fabric_linkage = importlib.util.module_from_spec(fabric_spec)
fabric_spec.loader.exec_module(fabric_linkage)


def long_path(path):
    r"""Windows APIs need the \\?\ prefix for paths over MAX_PATH."""
    resolved = str(Path(path).resolve())
    return Path('\\\\?\\' + resolved) if len(resolved) >= 240 else Path(resolved)


def fabric_audit(mc, baritone):
    # A .test/mc-<version>-named.jar copy survives the cache cleanups that
    # periodically remove .gradle/loom-cache artifacts.
    stable = ROOT / '.test' / ('mc-%s-named.jar' % mc)
    jar = stable if stable.is_file() else minecraft_jar(mc, 'mojmap')
    if jar is None:
        jar = first_existing(
            ['fabric/versions/%s/.gradle/loom-cache/minecraftMaven/**/*.jar' % mc])
    if jar is None and mc == '1.20.1':
        jar = first_existing(['fabric/.gradle/loom-cache/minecraftMaven/**/*.jar'])
    mappings = first_existing([
        '.dsh-tmp/gradle-home/caches/fabric-loom/%s/loom.mappings*/mappings-base.tiny' % mc])
    if not jar or not mappings:
        return None
    try:
        return fabric_linkage.audit(
            baritone, long_path(jar), Path(mappings), JDK)
    except Exception as error:
        return {'error': str(error)}


def projects():
    """Same project table as audit-baritone-matrix.py."""
    rows = []
    for base, loader in (('', 'Forge'), ('fabric', 'Fabric'),
                         ('neoforge', 'NeoForge')):
        directories = []
        if base and (ROOT / base / 'gradle.properties').is_file():
            directories.append(ROOT / base)
        elif not base and (ROOT / 'gradle.properties').is_file():
            directories.append(ROOT)
        version_dir = ROOT / base / 'versions' if base else ROOT / 'versions'
        if version_dir.is_dir():
            directories += sorted(p for p in version_dir.iterdir() if p.is_dir())
        for project in directories:
            props = project / 'gradle.properties'
            if not props.is_file():
                continue
            text = props.read_text('utf-8', errors='replace')
            match = re.search(r'^minecraft_version\s*=\s*(\S+)', text, re.M)
            if not match:
                continue
            mc = match.group(1).strip()
            jar = ROOT / 'build/release-v1.5' / (
                'WurstB+.Plus-1.5.1-%s-%s.jar' % (loader, mc))
            rows.append((project.relative_to(ROOT).as_posix(), loader, mc, jar))
    return rows


def embedded_baritone(release):
    """Return (path to a jar holding baritone classes, temp dir or None)."""
    with zipfile.ZipFile(release) as outer:
        names = outer.namelist()
        nested = [n for n in names
                  if n.startswith('META-INF/') and 'baritone' in n
                  and n.endswith('.jar')]
        if not nested:
            return release, None
        data = outer.read(nested[0])
    temp = tempfile.NamedTemporaryFile(
        suffix='.jar', delete=False, dir=str(ROOT / '.dsh-tmp'))
    temp.write(data)
    temp.close()
    return Path(temp.name), Path(temp.name)


def flavour(baritone):
    mojmap = intermediary = srg = 0
    with zipfile.ZipFile(baritone) as jar:
        for name in [n for n in jar.namelist() if n.endswith('.class')][:400]:
            data = jar.read(name)
            intermediary += len(re.findall(rb'net/minecraft/class_\d+', data))
            srg += len(re.findall(rb'\bm_\d+_|\bf_\d+_', data))
            mojmap += len(re.findall(rb'net/minecraft/client/Minecraft', data))
    if intermediary:
        return 'intermediary'
    if srg:
        return 'srg'
    return 'mojmap'


def _is_file(path):
    text = str(path)
    if len(text) >= 240 and not text.startswith('\\\\?\\'):
        text = '\\\\?\\' + str(Path(text).resolve())
    return os.path.isfile(text)


def first_existing(patterns):
    for pattern in patterns:
        matches = sorted(glob.glob(str(ROOT / pattern)))
        for match in matches:
            if _is_file(match):
                return Path(match)
    return None


def minecraft_jar(mc, kind):
    forge_versions = sorted(
        (ROOT / 'versions' / mc / 'build/fg_cache').glob(
            'net/minecraftforge/forge/%s-*/*-srg.jar' % mc))
    neoforge_srg = sorted(
        (ROOT / 'neoforge' / 'build/fg_cache').glob(
            'net/minecraftforge/forge/%s-*/*-srg.jar' % mc))
    joined = sorted((ROOT / '.dsh-tmp/gradle-home/caches/forge_gradle/mcp_repo/'
                     'net/minecraft/joined').glob('%s-*-srg.jar' % mc))
    if kind == 'srg':
        for candidate in forge_versions + neoforge_srg + joined:
            if candidate.is_file():
                return candidate
        return None
    # mojmap: prefer the stable .test copy (cache cleanups remove the rest).
    stable = ROOT / '.test' / ('mc-%s-named.jar' % mc)
    if stable.is_file():
        return stable
    patterns = [
        'neoforge/versions/%s/build/neoForm/*/steps/rename/output.jar' % mc,
        'neoforge/versions/%s/build/moddev/artifacts/*-merged.jar' % mc,
        'fabric/versions/%s/.gradle/loom-cache/minecraftMaven/net/minecraft/'
        'minecraft-merged-*/*/minecraft-merged-*.jar' % mc,
        '.dsh-tmp/gradle-home/caches/minecraftforge/forgegradle/mavenizer/'
        'caches/forge/net/minecraftforge/forge/%s-*/official/%s/recompiled.jar'
        % (mc, mc),
    ]
    candidate = first_existing(patterns)
    if candidate and 'minecraft-merged' in candidate.name and mc.startswith('26.'):
        return candidate
    if candidate:
        return candidate
    return None


def audit_jar(mc_jar, bari_jar):
    try:
        result = linkage.audit(mc_jar, bari_jar, JDK)
    except Exception as error:  # mapping/jar mismatch
        return {'error': str(error)}
    return {'checked': result['references_checked'],
            'unresolved': len(result['unresolved']),
            'first_unresolved': list(result['unresolved'])[:5]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path,
                        default=ROOT / 'report/baritone-linkage-matrix-v1.5.1.json')
    args = parser.parse_args()
    rows = []
    for project, loader, mc, release in projects():
        row = dict(project=project, loader=loader, minecraft=mc,
                   release=str(release.relative_to(ROOT)))
        if not release.is_file():
            row.update(verdict='MISSING')
            rows.append(row)
            print('MISSING  ', project)
            continue
        bari, temp = embedded_baritone(release)
        row['flavour'] = flavour(bari)
        mc_jar = minecraft_jar(mc, row['flavour'])
        row['minecraft_jar'] = str(mc_jar.relative_to(ROOT)) if mc_jar else None
        if row['flavour'] == 'intermediary' and loader == 'Fabric':
            outcome = fabric_audit(mc, bari)
            if outcome is None:
                row['verdict'] = 'NO_MAPPED_JAR'
            elif 'error' in outcome:
                row['error'] = outcome['error']
                row['verdict'] = 'ERROR'
            else:
                row['checked'] = outcome['references_checked']
                row['unresolved'] = len(outcome['unresolved'])
                row['first_unresolved'] = list(outcome['unresolved'])[:5]
                row['verdict'] = 'PASS' if row['unresolved'] == 0 else 'FAIL'
        elif mc_jar is None:
            row['verdict'] = 'NO_MAPPED_JAR'
        else:
            outcome = audit_jar(mc_jar, bari)
            row.update(outcome)
            if 'error' in outcome:
                row['verdict'] = 'ERROR'
            elif outcome['unresolved'] == 0:
                row['verdict'] = 'PASS'
            elif outcome['unresolved'] <= 3:
                row['verdict'] = 'FAIL'
            else:
                row['verdict'] = 'MAPPING_MISMATCH?'
        rows.append(row)
        print('%-6s %-10s %-8s %-8s %s' % (
            row['verdict'], row['loader'], mc, row['flavour'],
            row.get('checked', row.get('error', '')),
        ))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=2) + '\n',
                           encoding='utf-8')
    counts = {}
    for row in rows:
        counts[row['verdict']] = counts.get(row['verdict'], 0) + 1
    print('\n' + ', '.join('%s=%d' % item for item in sorted(counts.items())))
    print('report:', args.output)
    return 0 if counts.get('PASS', 0) == len(rows) else 1


if __name__ == '__main__':
    raise SystemExit(main())
