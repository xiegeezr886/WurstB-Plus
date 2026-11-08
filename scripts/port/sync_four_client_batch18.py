"""Merge weather read overrides while preserving independent changes."""
import argparse
import hashlib
import json
import shutil
from compile_four_client_matrix import wait_for_existing_build
from sync_four_client_batch import ROOT, PLAN, read, write


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    parser.add_argument('--exclude-target', action='append', default=[])
    parser.add_argument('--preview', action='store_true')
    args = parser.parse_args()
    batch = PLAN / 'batch18'
    stage = batch / 'src/net/wurstclient'
    manifest = PLAN / 'batch18-outputs.json'
    previous = json.loads(read(manifest)) if manifest.exists() else {}
    reviewed = json.loads(read(batch / 'reviewed-modules.json'))
    for tree in json.loads(read(PLAN / 'target-inventory.json')):
        if (args.target and tree['root'] not in args.target) or tree['root'] in args.exclude_target:
            continue
        if not args.preview:
            wait_for_existing_build(tree)
        java = ROOT / tree['root'] / 'src/main/java/net/wurstclient'
        planned, observed = {}, {}

        def original(file):
            observed[file] = file.read_bytes()
            backup = batch / 'before' / file.relative_to(ROOT)
            if not backup.exists() and not args.preview:
                backup.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, backup)
            return read(backup if backup.exists() else file)

        file = java / 'hacks/WeatherChangerHack.java'
        before = original(file)
        assert hashlib.sha256(before.encode()).hexdigest() in reviewed, file
        planned[file] = read(stage / 'hacks/WeatherChangerHack.java')
        file = java / 'mixin/WeatherChangerLevelMixin.java'
        assert not file.exists() or file.relative_to(ROOT).as_posix() in previous, file
        planned[file] = read(stage / 'mixin/WeatherChangerLevelMixin.java')
        configs = []
        for file in (ROOT / tree['root'] / 'src/main/resources').glob('*.mixins.json'):
            text = original(file)
            data = json.loads(text.lstrip('\ufeff'))
            if data.get('package') != 'net.wurstclient.mixin':
                continue
            assert 'WeatherChangerLevelMixin' not in data['client'], file
            planned[file] = text.replace('"client": [', '"client": [\n    "WeatherChangerLevelMixin",')
            configs.append(file)
        assert configs
        for file in planned:
            rel = file.relative_to(ROOT).as_posix()
            if rel in previous:
                assert hashlib.sha256(file.read_bytes()).hexdigest() == previous[rel], f'Concurrent edit: {file}'
            if file in observed:
                assert file.read_bytes() == observed[file], f'Changed during merge: {file}'
        for file, text in planned.items():
            dest = batch / 'generated' / file.relative_to(ROOT) if args.preview else file
            write(dest, text)
            if not args.preview:
                previous[file.relative_to(ROOT).as_posix()] = hashlib.sha256(file.read_bytes()).hexdigest()
        if not args.preview:
            write(manifest, json.dumps(previous, indent=2) + '\n')
        print('Batch18 ' + ('preview: ' if args.preview else 'synchronized: ') + tree['root'], flush=True)


if __name__ == '__main__':
    main()
