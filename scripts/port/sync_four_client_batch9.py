"""Merge native targeting, beacon selection and mount options across 66 trees."""
import argparse
import hashlib
import json
import re
import shutil
import subprocess
from sync_four_client_batch import ROOT, PLAN, read, write
from sync_four_client_batch2 import merge

STAGE = PLAN / 'batch9/src/net/wurstclient'


def targeting_hook(api):
    imports = '''package net.wurstclient.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
'''
    if 'private net.minecraft.world.phys.HitResult pick(net.minecraft.world.entity.Entity, double, double, float);' in api or 'private static net.minecraft.world.phys.HitResult pick(net.minecraft.world.entity.Entity, double, double, float);' in api:
        local = 'private static net.minecraft.world.phys.HitResult pick(' in api
        target = 'net.minecraft.client.player.LocalPlayer' if local else 'net.minecraft.client.renderer.GameRenderer'
        return imports + '''import com.llamalad7.mixinextras.injector.ModifyReturnValue;
@Mixin(''' + target + '''.class)
public abstract class NoMiningTraceMixin
{
	@ModifyReturnValue(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;", at = @At("RETURN"))
	private ''' + ('static ' if local else '') + '''HitResult wurstSelectBlock(HitResult original, Entity camera,
		double blockRange, double entityRange, float partialTicks)
	{
		var hax = WurstClient.INSTANCE.getHax();
		return hax == null ? original : hax.noMiningTraceHack
			.selectBlock(original, camera, blockRange, partialTicks);
	}
}
'''
    assert 'public void pick(float);' in api
    return imports + '''import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class NoMiningTraceMixin
{
	@Inject(method = "pick(F)V", at = @At("TAIL"))
	private void wurstSelectBlock(float partialTicks, CallbackInfo ci)
	{
		Minecraft mc = Minecraft.getInstance();
		var hax = WurstClient.INSTANCE.getHax();
		if(hax == null || mc.gameMode == null)
			return;
		HitResult selected = hax.noMiningTraceHack.selectBlock(mc.hitResult,
			mc.getCameraEntity(), hax.reachHack.isEnabled()
				? hax.reachHack.getBlockRange() : mc.gameMode.getPickRange(), partialTicks);
		if(selected != mc.hitResult)
		{
			mc.hitResult = selected;
			mc.crosshairPickEntity = null;
		}
	}
}
'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--target', action='append')
    parser.add_argument('--skip-building', action='store_true')
    args = parser.parse_args()
    manifest = PLAN / 'batch9-outputs.json'
    previous = json.loads(read(manifest)) if manifest.exists() else {}
    # Do not mutate a project while an external packaging or compile wrapper uses it.
    query = "@(Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -match 'org.gradle.wrapper.GradleWrapperMain' } | Select-Object -ExpandProperty CommandLine) | ConvertTo-Json -Compress"
    result = subprocess.run(['powershell', '-NoProfile', '-Command', query], capture_output=True, check=True)
    active = json.loads(result.stdout.decode('utf-8-sig') or '[]')
    if isinstance(active, str):
        active = [active]
    for tree in json.loads(read(PLAN / 'target-inventory.json')):
        if args.target and tree['root'] not in args.target:
            continue
        root = ROOT / tree['root']
        prefix = str(root).lower() + '\\'
        building = any(prefix + '\\gradle\\wrapper' in c.lower() or prefix + 'gradle\\wrapper' in c.lower() for c in active)
        if building and args.skip_building:
            print('Deferred active build:', tree['root'], flush=True)
            continue
        assert not building, f'Build currently uses {root}'
        java = root / 'src/main/java/net/wurstclient'
        api = read(PLAN / 'batch9' / f"hooks-{tree['mc']}.txt")
        planned = {}
        observed = {}

        def original(file):
            observed[file] = file.read_bytes()
            backup = PLAN / 'batch9/before' / file.relative_to(ROOT)
            if not backup.exists():
                backup.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(file, backup)
            return read(backup)

        for name in ['AutoMount', 'BetterBeacons']:
            relative = 'hacks/' + name + 'Hack.java'
            file = java / relative
            before = original(file)

            def adapt(text):
                if tree['mc'] in ['26.2', '26.3']:
                    text = text.replace('MC.screen', 'MC.gui.screen()')
                if name == 'AutoMount':
                    saddle = re.search(r'return !checkSaddle[^;]+;', before)
                    if saddle:
                        text = text.replace('return !checkSaddle.isChecked() || horse.isSaddled();', saddle[0])
                    for pattern in [r'import net\.minecraft\.world\.entity\.animal[^;]*AbstractHorse;', r'import net\.minecraft\.world\.entity\.vehicle[^;]*Boat;', r'import net\.minecraft\.world\.entity\.vehicle[^;]*AbstractMinecart;', r'MC\.gameMode\.interact\(MC\.player, closest,[^;]*;']:
                        old_match, new_match = re.search(pattern, text), re.search(pattern, before)
                        if old_match and new_match:
                            text = text.replace(old_match[0], new_match[0])
                    if 'import net.minecraft.world.phys.EntityHitResult;' in before:
                        text = text.replace('import net.minecraft.world.InteractionHand;', 'import net.minecraft.world.InteractionHand;\nimport net.minecraft.world.phys.EntityHitResult;')
                    if 'instanceof AbstractBoat' in before:
                        text = text.replace('instanceof Boat', 'instanceof AbstractBoat')
                    if 'interface net.minecraft.world.entity.Saddleable' not in api:
                        text = text.replace('net.minecraft.world.entity.Saddleable', 'net.minecraft.world.entity.Mob').replace('instanceof Saddleable mount', 'instanceof Mob mount')
                return text

            planned[file] = merge(before, adapt(read(PLAN / 'batch9/base' / relative)), adapt(read(STAGE / relative)), file)
        for relative in ['hacks/GhostHandHack.java', 'hacks/NoMiningTraceHack.java', 'mixinterface/IBeaconPowerButton.java', 'mixin/BeaconPowerButtonMixin.java', 'mixin/BetterBeaconsScreenMixin.java']:
            file = java / relative
            assert not file.exists() or file.relative_to(ROOT).as_posix() in previous, f'Existing new file: {file}'
            text = read(STAGE / relative)
            if relative.endswith('GhostHandHack.java'):
                if 'getPickRange();' not in api:
                    text = text.replace('MC.gameMode.getPickRange()', 'MC.player.blockInteractionRange()')
                # Modern direct access and arm animation use the same adaptations as FastUse/AutoShearer.
                if 'setRightClickDelay(int ' not in read(java / 'mixinterface/IMinecraftClient.java'):
                    accessor = java / 'mixin/MinecraftAccessor.java'
                    if accessor.exists():
                        assert 'setRightClickDelay(int ' in read(accessor), java
                        text = text.replace('mixinterface.IMinecraftClient', 'mixin.MinecraftAccessor').replace('(IMinecraftClient)', '(MinecraftAccessor)')
                    else:
                        assert 'MC.rightClickDelay' in read(java / 'hacks/FastUseHack.java'), java
                        text = text.replace('((IMinecraftClient)(Object)MC).setRightClickDelay(4);', 'MC.rightClickDelay = 4;')
                        text = text.replace('import net.wurstclient.mixinterface.IMinecraftClient;\n', '')
                swing = re.search(r'MC\.player\.swing\(hand[^;]*;', read(java / 'hacks/AutoShearerHack.java'))
                if swing:
                    text = text.replace('MC.player.swing(hand);', swing[0])
            if relative.endswith('BetterBeaconsScreenMixin.java') and 'Holder<net.minecraft.world.effect.MobEffect> primary' in api:
                text = text.replace('import net.minecraft.world.effect.MobEffect;', 'import net.minecraft.world.effect.MobEffect;\nimport net.minecraft.core.Holder;')
                text = text.replace('private MobEffect ', 'private Holder<MobEffect> ').replace('List<MobEffect>', 'List<Holder<MobEffect>>').replace('MobEffect effect =', 'Holder<MobEffect> effect =').replace('effect.getDisplayName()', 'effect.value().getDisplayName()')
            if relative.endswith('BetterBeaconsScreenMixin.java'):
                extra = read(PLAN / 'batch9' / f"extra-{tree['mc']}.txt")
                assert 'rebuildWidgets();' in extra
                if ' MOVEMENT_SPEED;' not in extra:
                    for old, new in [('MOVEMENT_SPEED', 'SPEED'), ('DIG_SPEED', 'HASTE'), ('DAMAGE_RESISTANCE', 'RESISTANCE'), ('JUMP', 'JUMP_BOOST'), ('DAMAGE_BOOST', 'STRENGTH')]:
                        assert ' ' + new + ';' in extra
                        text = text.replace('MobEffects.' + old, 'MobEffects.' + new)
            planned[file] = text
        hook = java / 'mixin/NoMiningTraceMixin.java'
        assert not hook.exists() or hook.relative_to(ROOT).as_posix() in previous, hook
        planned[hook] = '/* SPDX-License-Identifier: GPL-3.0 */\n' + targeting_hook(api)
        registry = java / 'hack/HackList.java'
        text = original(registry)
        marker = 'public final class HackList implements UpdateListener\n{\n'
        assert text.count(marker) == 1, registry
        additions = ''.join(f'\tpublic final {name}Hack {name[0].lower() + name[1:]}Hack = new {name}Hack();\n' for name in ['NoMiningTrace', 'GhostHand'])
        planned[registry] = text.replace(marker, marker + additions)
        configs = []
        for config in (root / 'src/main/resources').glob('*.mixins.json'):
            text = original(config)
            if json.loads(text.lstrip('\ufeff')).get('package') != 'net.wurstclient.mixin':
                continue
            planned[config] = text.replace('"client": [', '"client": [\n    "NoMiningTraceMixin",\n    "BeaconPowerButtonMixin",\n    "BetterBeaconsScreenMixin",')
            configs.append(config)
        assert configs, root
        translation = root / 'src/main/resources/assets/wurst/translations/zh_cn.json'
        text = original(translation)
        additions = {
            'hack.name.nominingtrace': '穿实体挖掘',
            'description.wurst.hack.nominingtrace': '穿过实体瞄准方块，可限制为持镐时生效并排除指定实体类型。',
            'hack.name.ghosthand': '隔墙交互',
            'description.wurst.hack.ghosthand': '右键沿视线交互墙后的容器，按住潜行键时暂停。',
        }
        data = json.loads(text.lstrip('\ufeff'))
        assert not any(key in data for key in additions), translation
        index = text.rfind('}')
        planned[translation] = text[:index].rstrip() + ',\n' + ',\n'.join('  ' + json.dumps(k) + ': ' + json.dumps(v, ensure_ascii=False) for k, v in additions.items()) + '\n}\n'
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
        print('Batch 9 synchronized:', tree['root'], flush=True)


if __name__ == '__main__':
    main()
