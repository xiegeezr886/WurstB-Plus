"""Offline release input checks. Never builds, signs in or uploads."""
import argparse
import io
import re
import sys
from pathlib import Path
from zipfile import BadZipFile, ZipFile


ROOT = Path(__file__).resolve().parents[1]
LOADERS = {'Forge': '.', 'Fabric': 'fabric', 'NeoForge': 'neoforge'}
VERSIONS = ('1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20.5', '1.20.6',
            '1.21', *(f'1.21.{i}' for i in range(1, 12)),
            '26.1', '26.1.1', '26.1.2', '26.2', '26.3')
BLOCKED_SOURCES = ('hacks/ForceOpHack.java', 'util/ForceOpDialog.java', 'hacks/CrashChestHack.java')
MARKERS = tuple(Path(name).stem.lower().encode('ascii') for name in BLOCKED_SOURCES)


def projects(root):
    for loader, directory in LOADERS.items():
        for mc in VERSIONS:
            if loader == 'Forge' and mc in ('1.20.5', '1.21.2'):
                continue
            path = root / directory
            yield f'{loader}-{mc}', path if mc == '1.20.1' else path / 'versions' / mc


def artifact_errors(archive, depth=0):
    if depth > 8:
        raise ValueError('nested JAR depth limit')
    with ZipFile(archive) as z:
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
            errors.extend(f'{name}: {e}' for e in artifact_errors(dist / name))
        except (BadZipFile, OSError, ValueError, RuntimeError) as e:
            errors.append(f'{name}: unreadable archive: {e}')
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
