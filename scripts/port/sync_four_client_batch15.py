"""Merge staged Parkour/Sneak behavior, retaining native modes and defaults."""
import argparse
import hashlib
import json
import shutil
from compile_four_client_matrix import wait_for_existing_build
from sync_four_client_batch import ROOT, PLAN, read, write
from sync_four_client_batch2 import merge


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    parser.add_argument('--preview', action='store_true')
    args = parser.parse_args()
    batch = PLAN / 'batch15'
    manifest = PLAN / 'batch15-outputs.json'
    previous = json.loads(read(manifest)) if manifest.exists() else {}
    for tree in json.loads(read(PLAN / 'target-inventory.json')):
        if args.target and tree['root'] not in args.target:
            continue
        if not args.preview:
            wait_for_existing_build(tree)
        api = read(batch / ('parkour-api-' + tree['mc'] + '.txt'))
        assert 'getBlockCollisions(net.minecraft.world.entity.Entity, net.minecraft.world.phys.AABB);' in api
        java = ROOT / tree['root'] / 'src/main/java/net/wurstclient'
        planned, observed = {}, {}
        for name in ['ParkourHack', 'SneakHack']:
            relative = 'hacks/' + name + '.java'
            file = java / relative
            observed[file] = file.read_bytes()
            backup = batch / 'before' / file.relative_to(ROOT)
            if not backup.exists() and not args.preview:
                backup.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, backup)
            original = read(backup if backup.exists() else file)
            modern = 'import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;' in original
            if name == 'ParkourHack':
                base, stage = batch / 'base', batch / 'src/net/wurstclient'
            else:
                base = batch / ('base-modern' if modern else 'base-legacy')
                stage = batch / ('modern' if modern else 'src') / 'net/wurstclient'
            planned[file] = merge(original, read(base / relative), read(stage / relative), file)
        for file in planned:
            rel = file.relative_to(ROOT).as_posix()
            if rel in previous:
                assert hashlib.sha256(file.read_bytes()).hexdigest() == previous[rel], f'Concurrent edit: {file}'
            assert file.read_bytes() == observed[file], f'Changed during merge: {file}'
        for file, text in planned.items():
            dest = batch / 'generated' / file.relative_to(ROOT) if args.preview else file
            write(dest, text)
            if not args.preview:
                previous[file.relative_to(ROOT).as_posix()] = hashlib.sha256(file.read_bytes()).hexdigest()
        if not args.preview:
            write(manifest, json.dumps(previous, indent=2) + '\n')
        print('Batch15 ' + ('preview: ' if args.preview else 'synchronized: ') + tree['root'], flush=True)


if __name__ == '__main__':
    main()
