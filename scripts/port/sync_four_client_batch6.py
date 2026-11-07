"""Merge Meteor movement options without replacing version-specific sources."""
import argparse
import hashlib
import json
import shutil
from sync_four_client_batch import ROOT, PLAN, read, write
from sync_four_client_batch2 import merge

STAGE = PLAN / 'batch6/src/net/wurstclient'
MERGED = ['hacks/' + name + 'Hack.java' for name in
          ['AirJump', 'AutoJump', 'Spider', 'ReverseStep', 'HighJump']]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    args = parser.parse_args()
    manifest = PLAN / 'batch6-outputs.json'
    previous = json.loads(read(manifest)) if manifest.exists() else {}
    for tree in json.loads(read(PLAN / 'target-inventory.json')):
        if args.target and tree['root'] not in args.target:
            continue
        root = ROOT / tree['root']
        java = root / 'src/main/java/net/wurstclient'
        planned = {}

        def original(file):
            backup = PLAN / 'batch6/before' / file.relative_to(ROOT)
            if not backup.exists():
                backup.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, backup)
            return read(backup)

        for name in MERGED:
            file = java / name
            text = merge(original(file), read(PLAN / 'batch6/base' / name),
                         read(STAGE / name), file)
            if tree['mc'] in ['26.2', '26.3']:
                text = text.replace('MC.screen', 'MC.gui.screen()')
            if name.endswith('AirJumpHack.java'):
                key_reference = read(java / 'hacks/ToggleSneakHack.java')
                if 'import net.minecraft.client.input.KeyEvent;' in key_reference:
                    text = text.replace('import net.minecraft.client.player.LocalPlayer;',
                        'import net.minecraft.client.player.LocalPlayer;\nimport net.minecraft.client.input.KeyEvent;')
                    text = text.replace('.matches(event.getKeyCode(), event.getScanCode())',
                        '.matches(new KeyEvent(event.getKeyCode(), event.getScanCode(), event.getModifiers()))')
            planned[file] = text
        hook = 'mixin/HighJumpMultiplierMixin.java'
        file = java / hook
        if file.exists() and file.relative_to(ROOT).as_posix() not in previous:
            raise ValueError(f'Untracked existing destination: {file}')
        planned[file] = read(STAGE / hook)
        configs = []
        for config in (root / 'src/main/resources').glob('*.mixins.json'):
            text = original(config)
            if json.loads(text.lstrip('\ufeff')).get('package') != 'net.wurstclient.mixin':
                continue
            assert 'HighJumpMultiplierMixin' not in text, config
            assert text.count('"client": [') == 1, config
            planned[config] = text.replace('"client": [', '"client": [\n    "HighJumpMultiplierMixin",')
            configs.append(config)
        assert configs, root
        # Verify all destinations before changing any file in the tree.
        for file in planned:
            rel = file.relative_to(ROOT).as_posix()
            if rel in previous:
                assert hashlib.sha256(file.read_bytes()).hexdigest() == previous[rel], f'Concurrent edit: {file}'
        for file, text in planned.items():
            write(file, text)
            previous[file.relative_to(ROOT).as_posix()] = hashlib.sha256(file.read_bytes()).hexdigest()
        write(manifest, json.dumps(previous, indent=2) + '\n')
        print('Batch 6 synchronized:', tree['root'])


if __name__ == '__main__':
    main()
