"""Offline release input checks. Never builds, signs in or uploads."""
import argparse
import io
import json
import re
import sys
import tomllib
from pathlib import Path
from zipfile import BadZipFile, ZipFile


ROOT = Path(__file__).resolve().parents[1]
LOADERS = {'Forge': '.', 'Fabric': 'fabric', 'NeoForge': 'neoforge'}
VERSIONS = ('1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20.5', '1.20.6',
            '1.21', *(f'1.21.{i}' for i in range(1, 12)),
            '26.1', '26.1.1', '26.1.2', '26.2', '26.3')
BLOCKED_SOURCES = ('hacks/ForceOpHack.java', 'util/ForceOpDialog.java', 'hacks/CrashChestHack.java',
                   'hacks/ThrowHack.java')
MARKERS = tuple(Path(name).stem.lower().encode('ascii') for name in BLOCKED_SOURCES)


def projects(root):
    for loader, directory in LOADERS.items():
        for mc in VERSIONS:
            if loader == 'Forge' and mc in ('1.20.5', '1.21.2'):
                continue
            path = root / directory
            yield f'{loader}-{mc}', path if mc == '1.20.1' else path / 'versions' / mc


def verify_metadata(z, target, release):
    loader, mc = target.split('-', 1)
    legacy_neo = loader == 'NeoForge' and mc in ('1.20.1', '1.20.2', '1.20.3', '1.20.4')
    descriptor = ('fabric.mod.json' if loader == 'Fabric' else 'META-INF/mods.toml'
                  if loader == 'Forge' or legacy_neo else 'META-INF/neoforge.mods.toml')
    if z.namelist().count(descriptor) != 1:
        raise ValueError(f'missing or duplicate {descriptor}')
    if z.getinfo(descriptor).file_size > 1024 * 1024:
        raise ValueError(f'oversized {descriptor}')
    text = z.read(descriptor).decode('utf-8-sig')
    if loader == 'Fabric':
        meta = json.loads(text)
        deps = meta['depends']
        if (meta['id'] != 'wurstpenguin' or 'fabricloader' not in deps or
                deps['minecraft'] not in (mc, '~' + mc, '=' + mc)):
            raise ValueError(f'{descriptor}: wrong mod, loader or Minecraft target')
        version = meta['version']
    else:
        meta = tomllib.loads(text)
        versions = [m['version'] for m in meta['mods'] if m['modId'] == 'wurstpenguin']
        if len(versions) != 1:
            raise ValueError(f'{descriptor}: expected exactly one wurstpenguin mod')
        version = versions[0]
        deps = meta['dependencies']['wurstpenguin']
        runtime = 'forge' if loader == 'Forge' or target == 'NeoForge-1.20.1' else 'neoforge'
        ranges = [d['versionRange'] for d in deps if d['modId'] == 'minecraft']
        if (not any(d['modId'] == runtime for d in deps) or len(ranges) != 1 or
                not re.fullmatch(r'\[' + re.escape(mc) + r'(?:\]|,\d+(?:\.\d+)+\))', ranges[0])):
            raise ValueError(f'{descriptor}: wrong loader or Minecraft target')
    allowed = {f'{release}-{target}'}
    if loader == 'NeoForge' and mc.startswith('26.'):
        allowed.add(release)  # These builds also use a plain version with an exact MC dependency.
    if version not in allowed:
        raise ValueError(f'{descriptor}: wrong internal version {version!r} for {release}-{target}')


def artifact_errors(archive, depth=0, *, target=None, release=None):
    if depth > 8:
        raise ValueError('nested JAR depth limit')
    with ZipFile(archive) as z:
        if target is not None:
            verify_metadata(z, target, release)
        for item in z.infolist():
            name = item.filename.lower()
            if name.endswith('.class'):
                if any(m in name.encode('utf-8') for m in MARKERS):
                    yield item.filename
                elif '/wurstclient/' in '/' + name:
                    data = z.read(item).lower()
                    if any(m in data for m in MARKERS):
                        yield item.filename
            elif name.endswith('.jar'):
                if item.file_size > 256 * 1024 * 1024:
                    raise ValueError(f'oversized nested JAR: {item.filename}')
                for error in artifact_errors(io.BytesIO(z.read(item)), depth + 1):
                    yield f'{item.filename}!{error}'


def check(root, dist, tag, only=None):
    if not re.fullmatch(r'v\d+\.\d+\.\d+(?:[-.][0-9A-Za-z.-]+)?', tag):
        return [f'invalid release tag: {tag}']
    matrix = dict(projects(root))
    targets = set(matrix) if only is None else set(only)
    if not targets or targets - matrix.keys():
        return ['empty or unknown release target']
    expected = {f'WurstB+.Plus-{tag[1:]}-{t}.jar' for t in targets}
    actual = {p.name for p in dist.glob('*.jar')}
    errors = [f'missing asset: {n}' for n in sorted(expected - actual)]
    if only is None:
        errors.extend(f'unexpected asset: {n}' for n in sorted(actual - expected))
    for target in sorted(targets):
        path = matrix[target]
        source = path / 'src/main/java/net/wurstclient'
        registry = source / 'hack/HackList.java'
        if not registry.is_file() or not (path / 'build.gradle').is_file():
            errors.append(f'{target}: missing project or registry')
        elif (any((source / name).exists() for name in BLOCKED_SOURCES) or
              any(m in registry.read_bytes().lower() for m in MARKERS)):
            errors.append(f'{target}: blocked implementation or registration in source')
        name = f'WurstB+.Plus-{tag[1:]}-{target}.jar'
        if name not in actual:
            continue
        try:
            errors.extend(f'{name}: {e}' for e in artifact_errors(dist / name, target=target, release=tag[1:]))
        except (BadZipFile, KeyError, OSError, TypeError, ValueError, RuntimeError) as e:
            errors.append(f'{name}: invalid archive or metadata: {e}')
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tag', required=True)
    parser.add_argument('--dist', type=Path, default=ROOT / 'build/release-v1.5')
    args = parser.parse_args()
    errors = check(ROOT, args.dist, args.tag)
    for error in errors:
        print(error)
    print(f'{len(dict(projects(ROOT)))} targets; {len(errors)} input errors. '
          'Input checks do not certify runtime compatibility.')
    return int(bool(errors))


if __name__ == '__main__':
    sys.exit(main())
