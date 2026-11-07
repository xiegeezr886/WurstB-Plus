"""Restore immutable NeoGradle libraries using verified local copies or Mojang."""
import hashlib
import json
from pathlib import Path
import urllib.request
import zipfile
from sync_four_client_batch import ROOT, PLAN


def verify_libraries(tree):
    root = ROOT / tree['root']
    cache = root / '.gradle/caches/minecraft/versions' / tree['mc']
    metadata = json.loads((cache / 'metadata.json').read_text(encoding='utf-8'))
    libraries = [entry['downloads']['artifact'] for entry in metadata['libraries']
                 if 'artifact' in entry.get('downloads', {})]
    base = Path.home() / '.gradle/caches/minecraft/libraries'
    lists = list((root / 'build/neoForm').glob('*/steps/listLibraries/libraries.txt'))
    assert len(lists) == 1, lists
    paths = {Path(line.removeprefix('-e=')).resolve() for line in lists[0].read_text(encoding='utf-8').splitlines() if line}
    assert paths == {(base / entry['path']).resolve() for entry in libraries}, lists[0]
    server_bytes = (cache / 'server.jar').read_bytes()
    assert hashlib.sha1(server_bytes).hexdigest() == metadata['downloads']['server']['sha1']
    report = []
    with zipfile.ZipFile(cache / 'server.jar') as server:
        for entry in libraries:
            dest = base / entry['path']
            assert dest.resolve().is_relative_to(base.resolve()), dest

            def matches(raw):
                return len(raw) == entry['size'] and hashlib.sha1(raw).hexdigest() == entry['sha1']

            if dest.is_file() and matches(dest.read_bytes()):
                report.append({'path': entry['path'], 'sha1': entry['sha1'], 'source': 'existing'})
                continue
            parts = Path(entry['path']).parts
            group, name, version, filename = '.'.join(parts[:-3]), *parts[-3:]
            candidates = (Path.home() / '.gradle/caches/modules-2/files-2.1' / group / name / version).glob('*/' + filename)
            raw, origin = None, None
            for candidate in candidates:
                content = candidate.read_bytes()
                if matches(content):
                    raw, origin = content, str(candidate)
                    break
            embedded = 'META-INF/libraries/' + entry['path']
            if raw is None and embedded in server.namelist():
                content = server.read(embedded)
                if matches(content):
                    raw, origin = content, 'verified-server.jar/' + embedded
            if raw is None:
                with urllib.request.urlopen(entry['url'], timeout=30) as response:
                    raw = response.read()
                assert matches(raw), entry['url']
                origin = entry['url']
            dest.parent.mkdir(parents=True, exist_ok=True)
            temporary = dest.with_name(dest.name + '.' + tree['mc'] + '.verified-temp')
            temporary.write_bytes(raw)
            temporary.replace(dest)
            report.append({'path': entry['path'], 'sha1': entry['sha1'], 'source': origin})
    (PLAN / ('neo-libraries-' + tree['mc'] + '.json')).write_text(json.dumps(report, indent=2), encoding='utf-8')
    print('Verified NeoGradle libraries:', tree['root'], len(libraries), flush=True)


if __name__ == '__main__':
    rows = json.loads((PLAN / 'target-inventory.json').read_text(encoding='utf-8'))
    for row in rows:
        if row['root'].startswith('neoforge/versions/1.20.'):
            verify_libraries(row)
