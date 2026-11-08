"""Merge FO AutoTrash settings and filtering while retaining per-version APIs."""
import argparse
import hashlib
import json
import shutil
from compile_four_client_matrix import wait_for_existing_build
from sync_four_client_batch import ROOT, PLAN, read, write
from sync_four_client_batch2 import merge

STAGE = PLAN / 'batch12/src/net/wurstclient'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    parser.add_argument('--preview', action='store_true')
    args = parser.parse_args()
    manifest = PLAN / 'batch12-outputs.json'
    previous = json.loads(read(manifest)) if manifest.exists() else {}
    for tree in json.loads(read(PLAN / 'target-inventory.json')):
        if args.target and tree['root'] not in args.target:
            continue
        if not args.preview:
            wait_for_existing_build(tree)
        java = ROOT / tree['root'] / 'src/main/java/net/wurstclient'
        file = java / 'hacks/AutoTrashHack.java'
        observed = file.read_bytes()
        backup = PLAN / 'batch12/before' / file.relative_to(ROOT)
        if not backup.exists() and not args.preview:
            backup.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(file, backup)
        before = read(backup if backup.exists() else file)
        api = read(PLAN / 'batch12' / f"api-{tree['mc']}.txt")

        def adapt(text):
            if 'getSelectedSlot()' in before:
                text = text.replace('MC.player.getInventory().selected', 'MC.player.getInventory().getSelectedSlot()')
            if tree['mc'] in ['26.2', '26.3']:
                text = text.replace('MC.screen', 'MC.gui.screen()')
            # Older branches call the interface through a cast; keep either baseline.
            if '((IClientPlayerInteractionManager)IMC.getInteractionManager())' not in before:
                text = text.replace('((IClientPlayerInteractionManager)IMC.getInteractionManager())', 'IMC.getInteractionManager()')
            if 'getEnchantmentsForCrafting(' in api:
                keys = read(PLAN / 'batch12' / f"keys-{tree['mc']}.txt")
                key_method = 'identifier' if ' identifier();' in keys else 'location'
                assert f' {key_method}();' in keys
                text = text.replace('EnchantmentHelper.getEnchantments(stack)', 'EnchantmentHelper.getEnchantmentsForCrafting(stack)')
                text = text.replace('result.put(BuiltInRegistries.ENCHANTMENT.getKey(entry.getKey()).toString(), entry.getValue());',
                    'entry.getKey().unwrapKey().ifPresent(key ->\n\t\t\t\tresult.put(key.' + key_method + '().toString(), entry.getIntValue()));')
            else:
                assert 'getEnchantments(net.minecraft.world.item.ItemStack)' in api
            if 'handleContainerInput(int, int, int, net.minecraft.world.inventory.ContainerInput, net.minecraft.world.entity.player.Player)' in api:
                text = text.replace('ClickType', 'ContainerInput').replace('handleInventoryMouseClick(', 'handleContainerInput(')
            else:
                assert 'handleInventoryMouseClick(int, int, int, net.minecraft.world.inventory.ClickType, net.minecraft.world.entity.player.Player)' in api
            return text

        planned = {file: merge(before, adapt(read(PLAN / 'batch12/base/hacks/AutoTrashHack.java')),
                               adapt(read(STAGE / 'hacks/AutoTrashHack.java')), file)}
        helper = java / 'util/EnchantmentRules.java'
        assert not helper.exists() or helper.relative_to(ROOT).as_posix() in previous, helper
        planned[helper] = read(STAGE / 'util/EnchantmentRules.java')
        assert file.read_bytes() == observed, file
        for target, text in planned.items():
            rel = target.relative_to(ROOT).as_posix()
            if rel in previous:
                assert hashlib.sha256(target.read_bytes()).hexdigest() == previous[rel], f'Concurrent edit: {target}'
            dest = PLAN / 'batch12/generated' / rel if args.preview else target
            write(dest, text)
            if not args.preview:
                previous[rel] = hashlib.sha256(target.read_bytes()).hexdigest()
        if not args.preview:
            write(manifest, json.dumps(previous, indent=2) + '\n')
        print('Batch 12 ' + ('preview: ' if args.preview else 'synchronized: ') + tree['root'], flush=True)


if __name__ == '__main__':
    main()
