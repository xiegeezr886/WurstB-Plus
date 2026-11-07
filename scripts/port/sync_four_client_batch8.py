"""Merge movement recovery, climbing timer and per-hand use across mappings."""
import argparse
import hashlib
import json
import re
import shutil
from sync_four_client_batch import ROOT, PLAN, read, write
from sync_four_client_batch2 import merge

STAGE = PLAN / 'batch8/src/net/wurstclient'
MERGED = ['hacks/' + n + 'Hack.java' for n in ['AntiVoid', 'FastClimb', 'Timer', 'AutoShearer', 'FastUse']]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    args = parser.parse_args()
    manifest = PLAN / 'batch8-outputs.json'
    previous = json.loads(read(manifest)) if manifest.exists() else {}
    for tree in json.loads(read(PLAN / 'target-inventory.json')):
        if args.target and tree['root'] not in args.target:
            continue
        root = ROOT / tree['root']
        java = root / 'src/main/java/net/wurstclient'
        planned = {}

        def original(file):
            backup = PLAN / 'batch8/before' / file.relative_to(ROOT)
            if not backup.exists():
                backup.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, backup)
            return read(backup)

        for name in MERGED:
            file = java / name
            before = original(file)

            def adapt(text):
                if tree['mc'] in ['26.2', '26.3']:
                    text = text.replace('MC.screen', 'MC.gui.screen()')
                if name.endswith('AntiVoidHack.java') and '.getMinY()' in before:
                    text = text.replace('.getMinBuildHeight()', '.getMinY()')
                if name.endswith('FastUseHack.java') and 'MC.startUseItem();' in before:
                    text = text.replace('((net.wurstclient.mixin.MinecraftAccessor)(Object)MC)\n\t\t\t.setRightClickDelay(0);', 'MC.rightClickDelay = 0;')
                    text = text.replace('((net.wurstclient.mixin.MinecraftAccessor)(Object)MC)\n\t\t\t\t\t.invokeStartUseItem();', 'MC.startUseItem();')
                    text = text.replace('\t\tvar accessor = (net.wurstclient.mixin.MinecraftAccessor)(Object)MC;\n', '')
                    text = text.replace('accessor.getRightClickDelay()', 'MC.rightClickDelay').replace('accessor.invokeStartUseItem()', 'MC.startUseItem()')
                elif name.endswith('FastUseHack.java') and 'MC).setRightClickDelay(0);' in before:
                    text = text.replace('MC)\n\t\t\t.setRightClickDelay(0);', 'MC).setRightClickDelay(0);')
                    text = text.replace('MC)\n\t\t\t\t\t.invokeStartUseItem();', 'MC).invokeStartUseItem();')
                if name.endswith('AutoShearerHack.java'):
                    if 'MC.player.getInventory().selected = (slot);' in before:
                        text = text.replace('MC.player.getInventory().selected = slot;', 'MC.player.getInventory().selected = (slot);')
                    for pattern in [r'import net\.minecraft\.world\.entity\.animal[^;]*Sheep;',
                                    r'MC\.player\.swing\(hand[^;]*;', r'MC\.gameMode\.interact\(MC\.player, target,[^;]*;']:
                        old_match = re.search(pattern, text)
                        new_match = re.search(pattern, before)
                        if old_match and new_match:
                            text = text.replace(old_match[0], new_match[0])
                    if '.setSelectedSlot(' in before:
                        text = re.sub(r'MC\.player\.getInventory\(\)\.selected = (\w+);', r'MC.player.getInventory().setSelectedSlot(\1);', text)
                        text = text.replace('MC.player.getInventory().selected', 'MC.player.getInventory().getSelectedSlot()')
                return text

            old = adapt(read(PLAN / 'batch8/base' / name))
            if name.endswith('AntiVoidHack.java') and '.getMinY() * 16' in before:
                # getMinY returns blocks; the inherited section conversion was wrong.
                old = old.replace('.getMinY()', '.getMinY() * 16')
            planned[file] = merge(before, old, adapt(read(STAGE / name)), file)
        hook = java / 'mixin/FastUseCooldownMixin.java'
        if hook.exists() and hook.relative_to(ROOT).as_posix() not in previous:
            raise ValueError(f'Untracked existing hook: {hook}')
        planned[hook] = read(STAGE / 'mixin/FastUseCooldownMixin.java')
        configs = []
        for config in (root / 'src/main/resources').glob('*.mixins.json'):
            text = original(config)
            if json.loads(text.lstrip('\ufeff')).get('package') != 'net.wurstclient.mixin':
                continue
            assert 'FastUseCooldownMixin' not in text, config
            planned[config] = text.replace('"client": [', '"client": [\n    "FastUseCooldownMixin",')
            configs.append(config)
        assert configs, root
        for file in planned:
            rel = file.relative_to(ROOT).as_posix()
            if rel in previous:
                assert hashlib.sha256(file.read_bytes()).hexdigest() == previous[rel], f'Concurrent edit: {file}'
        for file, text in planned.items():
            write(file, text)
            previous[file.relative_to(ROOT).as_posix()] = hashlib.sha256(file.read_bytes()).hexdigest()
        write(manifest, json.dumps(previous, indent=2) + '\n')
        print('Batch 8 synchronized:', tree['root'])


if __name__ == '__main__':
    main()
