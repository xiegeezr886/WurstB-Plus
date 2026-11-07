"""Merge nearby messages, XP use and selected local effect preservation."""
import argparse
import hashlib
import json
import shutil
from sync_four_client_batch import ROOT, PLAN, read, write
from sync_four_client_batch2 import merge

STAGE = PLAN / 'batch7/src/net/wurstclient'
MERGED = ['hacks/' + name + 'Hack.java' for name in
          ['MessageAura', 'EXPThrower', 'PotionSaver']]

def effect_hook(name, returns_int):
    # The old variants contained only this callback. Normalize its descriptor
    # using inspected game bytecode, including the obsolete mapDuration variant.
    callback = 'CallbackInfoReturnable<Integer>' if returns_int else 'CallbackInfo'
    imported_callback = 'CallbackInfoReturnable' if returns_int else 'CallbackInfo'
    shadow = '\t@org.spongepowered.asm.mixin.Shadow\n\tprivate int duration;\n' if returns_int else ''
    cancel = 'ci.setReturnValue(duration);' if returns_int else 'ci.cancel();'
    descriptor = 'I' if returns_int else 'V'
    return f'''/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 * GPL-3.0. Selected-effect behavior reference: Meteor Client (Meteor Development).
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.{imported_callback};
import net.minecraft.world.effect.MobEffectInstance;
import net.wurstclient.WurstClient;

@Mixin(MobEffectInstance.class)
public abstract class {name} implements Comparable<MobEffectInstance>
{{
{shadow}
\t@Inject(at = @At("HEAD"), method = "tickDownDuration(){descriptor}", cancellable = true)
\tprivate void onUpdateDuration({callback} ci)
\t{{
\t\tif(WurstClient.INSTANCE.getHax() != null
\t\t\t&& WurstClient.INSTANCE.getHax().potionSaverHack.shouldFreeze((MobEffectInstance)(Object)this))
\t\t\t{cancel}
\t}}
}}
'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    args = parser.parse_args()
    manifest = PLAN / 'batch7-outputs.json'
    previous = json.loads(read(manifest)) if manifest.exists() else {}
    for tree in json.loads(read(PLAN / 'target-inventory.json')):
        if args.target and tree['root'] not in args.target:
            continue
        java = ROOT / tree['root'] / 'src/main/java/net/wurstclient'
        planned = {}

        def original(file):
            backup = PLAN / 'batch7/before' / file.relative_to(ROOT)
            if not backup.exists():
                backup.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, backup)
            return read(backup)

        api = read(PLAN / 'batch7' / ('api-' + tree['mc'] + '.txt'))
        for name in MERGED:
            file = java / name
            text = merge(original(file), read(PLAN / 'batch7/base' / name), read(STAGE / name), file)
            if tree['mc'] in ['26.2', '26.3']:
                text = text.replace('MC.screen', 'MC.gui.screen()')
            if name.endswith('EXPThrowerHack.java') and 'inventory.setSelectedSlot(' in read(java / 'util/InventoryUtils.java'):
                text = text.replace('player.getInventory().selected = oldSlot;', 'player.getInventory().setSelectedSlot(oldSlot);')
                text = text.replace('player.getInventory().selected', 'player.getInventory().getSelectedSlot()')
            if name.endswith('PotionSaverHack.java') and 'Holder<net.minecraft.world.effect.MobEffect> getEffect()' in api:
                text = text.replace('instance.getEffect()', 'instance.getEffect().value()')
            planned[file] = text
        hooks = []
        for file in (java / 'mixin').glob('*EffectInstanceMixin.java'):
            text = original(file)
            if '.potionSaverHack.' not in text:
                continue
            planned[file] = effect_hook(file.stem, 'int tickDownDuration();' in api)
            hooks.append(file)
        if not hooks:
            file = java / 'mixin/StatusEffectInstanceMixin.java'
            assert not file.exists(), file
            planned[file] = effect_hook(file.stem, 'int tickDownDuration();' in api)
            hooks.append(file)
        # Check the registered callback shape against the actual game class.
        for config in (ROOT / tree['root'] / 'src/main/resources').glob('*.mixins.json'):
            text = original(config)
            data = json.loads(text.lstrip('\ufeff'))
            if data.get('package') != 'net.wurstclient.mixin':
                continue
            registered = set(data.get('client', []) + data.get('mixins', []))
            if not any(file.stem in registered for file in hooks):
                selected = next((file for file in hooks if file.stem == 'StatusEffectInstanceMixin'), hooks[0])
                assert text.count('"client": [') == 1, config
                planned[config] = text.replace('"client": [', '"client": [\n    "' + selected.stem + '",')
                registered.add(selected.stem)
            for file in hooks:
                if file.stem not in registered:
                    continue
                expected = 'tickDownDuration()V' if 'void tickDownDuration();' in api else 'tickDownDuration()I'
                assert expected in planned[file], (file, expected)
        for file in planned:
            rel = file.relative_to(ROOT).as_posix()
            if rel in previous:
                assert hashlib.sha256(file.read_bytes()).hexdigest() == previous[rel], f'Concurrent edit: {file}'
        for file, text in planned.items():
            write(file, text)
            previous[file.relative_to(ROOT).as_posix()] = hashlib.sha256(file.read_bytes()).hexdigest()
        write(manifest, json.dumps(previous, indent=2) + '\n')
        print('Batch 7 synchronized:', tree['root'])


if __name__ == '__main__':
    main()
