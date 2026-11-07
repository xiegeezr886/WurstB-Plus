"""Merge direction walking, name-tag targeting and rotation behavior."""
import argparse
import hashlib
import json
import re
import shutil
from compile_four_client_matrix import wait_for_existing_build
from sync_four_client_batch import ROOT, PLAN, read, write
from sync_four_client_batch2 import merge

STAGE = PLAN / 'batch10/src/net/wurstclient'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    args = parser.parse_args()
    manifest = PLAN / 'batch10-outputs.json'
    previous = json.loads(read(manifest)) if manifest.exists() else {}
    for tree in json.loads(read(PLAN / 'target-inventory.json')):
        if args.target and tree['root'] not in args.target:
            continue
        wait_for_existing_build(tree)
        root = ROOT / tree['root']
        java = root / 'src/main/java/net/wurstclient'
        api = read(PLAN / 'batch10' / f"api-{tree['mc']}.txt")
        assert 'public void move(net.minecraft.world.entity.MoverType, net.minecraft.world.phys.Vec3);' in api
        planned = {}
        observed = {}

        def original(file):
            observed[file] = file.read_bytes()
            backup = PLAN / 'batch10/before' / file.relative_to(ROOT)
            if not backup.exists():
                backup.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, backup)
            return read(backup)

        for name in ['AutoWalk', 'AutoNametag', 'Rotation']:
            relative = 'hacks/' + name + 'Hack.java'
            file = java / relative
            before = original(file)

            def adapt(text):
                if tree['mc'] in ['26.2', '26.3']:
                    text = text.replace('MC.screen', 'MC.gui.screen()')
                if name == 'AutoWalk':
                    if 'matches(net.minecraft.client.input.KeyEvent)' in api:
                        text = text.replace('key.matches(event.getKeyCode(), event.getScanCode())',
                            'key.matches(new net.minecraft.client.input.KeyEvent(event.getKeyCode(), event.getScanCode(), event.getModifiers()))')
                        assert 'net.minecraft.client.input.MouseButtonEvent(double, double, net.minecraft.client.input.MouseButtonInfo);' in api
                        text = text.replace('key.matchesMouse(button)',
                            'key.matchesMouse(new net.minecraft.client.input.MouseButtonEvent(0, 0, new net.minecraft.client.input.MouseButtonInfo(button, 0)))')
                if name == 'AutoNametag':
                    for pattern in [r'import net\.minecraft\.world\.entity\.animal[^;]*Animal;', r'import net\.minecraft\.world\.entity\.npc[^;]*Villager;', r'MC\.gameMode\.interact\(MC\.player, target,[^;]*;', r'MC\.player\.swing\(hand[^;]*;', r'Component tagNameComponent\s*=\s*[^;]+;']:
                        old, new = re.search(pattern, text), re.search(pattern, before)
                        if old and new:
                            text = text.replace(old[0], new[0])
                    if 'DataComponents.CUSTOM_NAME' in before:
                        text = text.replace('import net.minecraft.network.chat.Component;', 'import net.minecraft.core.component.DataComponents;\nimport net.minecraft.network.chat.Component;')
                    if '.setSelectedSlot(' in before:
                        text = re.sub(r'MC\.player\.getInventory\(\)\.selected = (\w+);', r'MC.player.getInventory().setSelectedSlot(\1);', text)
                        text = text.replace('MC.player.getInventory().selected', 'MC.player.getInventory().getSelectedSlot()')
                    elif '.selected = (slot);' in before:
                        text = text.replace('.selected = slot;', '.selected = (slot);')
                return text

            planned[file] = merge(before, adapt(read(PLAN / 'batch10/base' / relative)), adapt(read(STAGE / relative)), file)
        hook = java / 'mixin/AutoWalkMovementMixin.java'
        assert not hook.exists() or hook.relative_to(ROOT).as_posix() in previous
        planned[hook] = read(STAGE / 'mixin/AutoWalkMovementMixin.java')
        configs = []
        for config in (root / 'src/main/resources').glob('*.mixins.json'):
            text = original(config)
            if json.loads(text.lstrip('\ufeff')).get('package') == 'net.wurstclient.mixin':
                planned[config] = text.replace('"client": [', '"client": [\n    "AutoWalkMovementMixin",')
                configs.append(config)
        assert configs, root
        for file in planned:
            rel = file.relative_to(ROOT).as_posix()
            if rel in previous:
                assert hashlib.sha256(file.read_bytes()).hexdigest() == previous[rel], f'Concurrent edit: {file}'
            if file in observed:
                assert file.read_bytes() == observed[file], f'Changed during merge: {file}'
        for file, text in planned.items():
            write(file, text)
            previous[file.relative_to(ROOT).as_posix()] = hashlib.sha256(file.read_bytes()).hexdigest()
        write(manifest, json.dumps(previous, indent=2) + '\n')
        print('Batch 10 synchronized:', tree['root'], flush=True)


if __name__ == '__main__':
    main()
