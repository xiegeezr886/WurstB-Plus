"""Restore Mojang libraries for a selected NeoGradle target; verify every hash."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import os
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[2]


def valid(path, entry):
    return (path.is_file() and path.stat().st_size == entry['size']
            and hashlib.sha1(path.read_bytes()).hexdigest() == entry['sha1'])


def install_verified(dest, data, entry):
    """Publish complete files atomically; Neo targets share the library cache."""
    if len(data) != entry['size'] or hashlib.sha1(data).hexdigest() != entry['sha1']:
        raise ValueError('Mojang cache hash mismatch: ' + str(dest))
    dest.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=dest.parent, delete=False) as stream:
        temporary = Path(stream.name)
        stream.write(data)
    try:
        os.replace(temporary, dest)
    finally:
        temporary.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('target')
    args = parser.parse_args()
    project = (ROOT / args.target).resolve()
    if not project.is_relative_to(ROOT / 'neoforge/versions'):
        raise ValueError('Expected a NeoForge version project')
    cache = ROOT / '.dsh-tmp/gradle-home/caches'
    version_cache = project / '.gradle/caches/minecraft/versions' / project.name
    meta = json.loads((version_cache / 'metadata.json').read_text())
    verified = restored = 0
    for entry in [x['downloads']['artifact'] for x in meta['libraries'] if x.get('downloads', {}).get('artifact')]:
        dest = cache / 'minecraft/libraries' / entry['path']
        if not valid(dest, entry):
            candidates = list((cache / 'ng_execute').glob('*/output/' + entry['path']))
            candidates += list((cache / 'modules-2/files-2.1').glob('*/*/*/*/' + Path(entry['path']).name))
            found = next((p for p in candidates if valid(p, entry)), None)
            dest.parent.mkdir(parents=True, exist_ok=True)
            if found:
                install_verified(dest, found.read_bytes(), entry)
            else:
                data = urllib.request.urlopen(entry['url'], timeout=60).read()
                if len(data) != entry['size'] or hashlib.sha1(data).hexdigest() != entry['sha1']:
                    raise ValueError('Downloaded library hash mismatch: ' + entry['path'])
                install_verified(dest, data, entry)
            restored += 1
        assert valid(dest, entry), dest
        verified += 1
    for key, filename in [('client','client.jar'),('server','server.jar'),('client_mappings','client.txt'),('server_mappings','server.txt')]:
        entry = meta['downloads'][key]
        dest = version_cache / filename
        if not valid(dest, entry):
            shared = cache / 'fabric-loom' / project.name / ('minecraft-' + filename)
            data = shared.read_bytes() if valid(shared, entry) else urllib.request.urlopen(entry['url'], timeout=60).read()
            if len(data) != entry['size'] or hashlib.sha1(data).hexdigest() != entry['sha1']:
                raise ValueError('Mojang executable hash mismatch: ' + key)
            install_verified(dest, data, entry)
    print(json.dumps({'target': args.target, 'verified_libraries': verified, 'restored_libraries': restored, 'verified_mojang_files': 4}))


if __name__ == '__main__':
    main()
