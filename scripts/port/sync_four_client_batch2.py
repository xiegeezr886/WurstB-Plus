"""Apply reviewed batch-two hunks; do not replace version-specific class bodies."""
from pathlib import Path
import argparse
import difflib
import hashlib
import json
import shutil
import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sync_four_client_batch import ROOT, PLAN, read, write

STAGE = PLAN / 'batch2/src/net/wurstclient'
MERGED = ['hacks/AutoTextHack.java', 'hacks/AutoLeaveHack.java',
          'hacks/AutoReconnectHack.java', 'hacks/TreeBotHack.java']
NEW = ['util/ChatSequence.java', 'util/AutoLeaveTriggers.java', 'hacks/treebot/SaplingFarm.java']

def merge(source, old, new, label, context=3):
    oldlines, newlines = old.splitlines(True), new.splitlines(True)
    original = source
    edits = list(difflib.SequenceMatcher(a=oldlines, b=newlines, autojunk=False).get_grouped_opcodes(context))
    for group in reversed(edits):
        before = ''.join(oldlines[group[0][1]:group[-1][2]])
        after = ''.join(newlines[group[0][3]:group[-1][4]])
        if source.count(after) == 1:
            continue
        if source.count(before) != 1:
            if context > 1:
                return merge(original, old, new, label, context - 1)
            raise ValueError(f'Cannot safely merge {label}: {before[:180]!r}')
        source = source.replace(before, after, 1)
    return source

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    args = parser.parse_args()
    targets = json.loads(read(PLAN / 'target-inventory.json'))
    outputs = {}
    for tree in targets:
        if args.target and tree['root'] not in args.target:
            continue
        target = ROOT / tree['root']
        java = target / 'src/main/java/net/wurstclient'
        # Merge from each tree's immutable pre-batch copy, then verify that an
        # existing output was not independently edited before replacing it.
        planned = {}
        for name in MERGED:
            file = java / name
            backup = PLAN / 'before' / file.relative_to(ROOT)
            if not backup.exists():
                backup.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(file, backup)
            text = merge(read(backup), read(PLAN / 'batch2/base' / name), read(STAGE / name), file)
            if name.endswith('AutoLeaveHack.java') and not tree['mc'].startswith('1.20.'):
                text = text.replace('((net.wurstclient.mixin.AutoLeavePortalAccessor)(Object)MC.player).wurstIsInsidePortal()',
                                    'MC.player.portalProcess != null && MC.player.portalProcess.isInsidePortalThisTick()')
            planned[file] = text
        for name in NEW:
            text = read(STAGE / name)
            if name.endswith('SaplingFarm.java'):
                inventory_utils = read(java / 'util/InventoryUtils.java')
                if 'inventory.setSelectedSlot(' in inventory_utils:
                    text = text.replace('MC.player.getInventory().selected = grow ? boneSlot : oldSlot;',
                                        'MC.player.getInventory().setSelectedSlot(grow ? boneSlot : oldSlot);')
                    text = text.replace('MC.player.getInventory().selected = oldSlot;',
                                        'MC.player.getInventory().setSelectedSlot(oldSlot);')
                    text = text.replace('MC.player.getInventory().selected', 'MC.player.getInventory().getSelectedSlot()')
                if tree['mc'] in ['26.2', '26.3']:
                    text = text.replace('MC.screen', 'MC.gui.screen()')
            planned[java / name] = text
        if tree['mc'].startswith('1.20.'):
            name = 'mixin/AutoLeavePortalAccessor.java'
            planned[java / name] = read(STAGE / name)
            for config in (target / 'src/main/resources').glob('*.mixins.json'):
                text = read(config)
                data = json.loads(text.lstrip('\ufeff'))
                if data.get('package') == 'net.wurstclient.mixin' and 'AutoLeavePortalAccessor' not in data.get('client', []):
                    planned[config] = text.replace('"client": [', '"client": [\n    "AutoLeavePortalAccessor",')
        previous_file = PLAN / 'batch2-outputs.json'
        previous = json.loads(read(previous_file)) if previous_file.exists() else {}
        for file, text in planned.items():
            name = file.relative_to(ROOT).as_posix()
            if name in previous and file.exists():
                assert hashlib.sha256(file.read_bytes()).hexdigest() == previous[name], f'Concurrent edit: {file}'
            write(file, text)
            outputs[name] = hashlib.sha256(file.read_bytes()).hexdigest()
        previous.update(outputs)
        write(previous_file, json.dumps(previous, indent=2) + '\n')
        print('Batch 2 synchronized:', tree['root'])

if __name__ == '__main__':
    main()
