"""Replace reviewed deferred NoRotate variants with immediate handler callbacks."""
import argparse
import hashlib
import json
import shutil
from compile_four_client_matrix import wait_for_existing_build
from sync_four_client_batch import ROOT, PLAN, read, write

STAGE = PLAN / 'batch13/src/net/wurstclient'
# All four native variants were compared; only packet layout and deferred
# rotation detection differ. Refuse replacement of independently changed code.
REVIEWED = {
    'd8be0e5060871e6622e9e01456475743a937966163578eb0c181a641780b58c5',
    'd46c732e1275dfa90131554dcae0bb2beb66e5e53b02ae3b0ca7f3a881caf236',
    'db981e5fc3d71ae271ac673f208d8e0e448fa1e5a19b58a05b25738cbace8a0b',
    '8f4ea8a86ca21eecb5270b207cbd6d3e59d389f97b3095d1388563d87849053a',
}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    parser.add_argument('--preview', action='store_true')
    args = parser.parse_args()
    manifest = PLAN / 'batch13-outputs.json'
    previous = json.loads(read(manifest)) if manifest.exists() else {}
    for tree in json.loads(read(PLAN / 'target-inventory.json')):
        if args.target and tree['root'] not in args.target:
            continue
        if not args.preview:
            wait_for_existing_build(tree)
        root = ROOT / tree['root']
        java = root / 'src/main/java/net/wurstclient'
        api = read(PLAN / 'batch13' / f"api-{tree['mc']}.txt")
        for signature in ['public void handleMovePlayer(net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket);',
                          'public void handleLookAt(net.minecraft.network.protocol.game.ClientboundPlayerLookAtPacket);',
                          *['public float ' + name + ';' for name in ['yHeadRot', 'yHeadRotO', 'yBodyRot', 'yBodyRotO', 'yRotO', 'xRotO']]]:
            assert signature in api, (tree['mc'], signature)
        planned, observed = {}, {}

        def original(file):
            observed[file] = file.read_bytes()
            backup = PLAN / 'batch13/before' / file.relative_to(ROOT)
            if backup.exists():
                return read(backup)
            if not args.preview:
                backup.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, backup)
            return read(file)

        file = java / 'hacks/NoRotateHack.java'
        before = original(file)
        assert hashlib.sha256(before.encode('utf-8')).hexdigest() in REVIEWED, file
        planned[file] = read(STAGE / 'hacks/NoRotateHack.java')
        file = java / 'hacks/PortalGodModeHack.java'
        before = original(file)
        assert hashlib.sha256(before.encode('utf-8')).hexdigest() == '592621aa93425533b485fa9658fe645b95355ce7148bfbc9274eebb050aab1a5', file
        planned[file] = read(STAGE / 'hacks/PortalGodModeHack.java')
        file = java / 'mixin/NoRotatePacketMixin.java'
        assert not file.exists() or file.relative_to(ROOT).as_posix() in previous, file
        hook = read(STAGE / 'mixin/NoRotatePacketMixin.java')
        if 'public void handleRotatePlayer(net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket);' in api:
            marker = '"handleLookAt(Lnet/minecraft/network/protocol/game/ClientboundPlayerLookAtPacket;)V"'
            hook = hook.replace(marker, marker + ',\n\t\t"handleRotatePlayer(Lnet/minecraft/network/protocol/game/ClientboundPlayerRotationPacket;)V"')
        planned[file] = hook
        configs = []
        for config in (root / 'src/main/resources').glob('*.mixins.json'):
            text = original(config)
            if json.loads(text.lstrip('\ufeff')).get('package') != 'net.wurstclient.mixin':
                continue
            assert '"NoRotatePacketMixin"' not in text, config
            planned[config] = text.replace('"client": [', '"client": [\n    "NoRotatePacketMixin",')
            configs.append(config)
        assert configs, root
        for file in planned:
            rel = file.relative_to(ROOT).as_posix()
            if rel in previous:
                assert hashlib.sha256(file.read_bytes()).hexdigest() == previous[rel], f'Concurrent edit: {file}'
            if file in observed:
                assert file.read_bytes() == observed[file], f'Changed during merge: {file}'
        for file, text in planned.items():
            dest = PLAN / 'batch13/generated' / file.relative_to(ROOT) if args.preview else file
            write(dest, text)
            if not args.preview:
                previous[file.relative_to(ROOT).as_posix()] = hashlib.sha256(file.read_bytes()).hexdigest()
        if not args.preview:
            write(manifest, json.dumps(previous, indent=2) + '\n')
        print('Batch 13 ' + ('preview: ' if args.preview else 'synchronized: ') + tree['root'], flush=True)


if __name__ == '__main__':
    main()
