# -*- coding: utf-8 -*-
"""Patch existing Wurst mixins with the Soar modules' hooks.

The script is idempotent: every inserted block has a unique soar$ marker and
every overlay change checks for the new hack before running.
"""
import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

MARK = '// WurstB+ Soar extras'


def version_of(project):
    parts = project.parts
    try:
        i = parts.index('versions')
        return parts[i + 1]
    except ValueError:
        return ''


def insert_import(text, imp):
    if imp in text:
        return text
    m = re.search(r'^import .*?;\r?\n', text, re.M)
    if not m:
        return text
    return text[:m.start()] + 'import ' + imp + ';\n' + text[m.start():]


def insert_before_final_brace(text, code):
    idx = text.rfind('}')
    if idx == -1:
        raise RuntimeError('no class closing brace')
    return text[:idx] + code + '\n' + text[idx:]


def read(path):
    return path.read_text(encoding='utf-8')


def write(path, text):
    path.write_text(text, encoding='utf-8', newline='\n')


def patch_minecraft(path):
    text = read(path)
    if 'soar$onGetFps' in text:
        return False
    text = insert_import(text, 'net.wurstclient.hack.HackList')
    if 'missTime' not in text:
        if '@Shadow' not in text:
            text = insert_import(text, 'org.spongepowered.asm.mixin.Shadow')
        text = insert_before_final_brace(
            text, '\n\t@Shadow\n\tpublic int missTime;\n')
    code = r'''
	@Inject(method = "getFps()I", at = @At("RETURN"), cancellable = true)
	private void soar$onGetFps(
		org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.fpsSpooferHack.isEnabled())
			return;

		cir.setReturnValue(
			cir.getReturnValueI() * hax.fpsSpooferHack.getMultiplier());
	}

	@Inject(method = "tick()V", at = @At("HEAD"))
	private void soar$onTickHitDelayFix(
		org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax != null && hax.hitDelayFixHack.isEnabled())
			missTime = 0;
	}
'''
    text = insert_before_final_brace(text, code)
    write(path, text)
    return True


def patch_living_entity(path, version):
    text = read(path)
    if 'soar$onGetCurrentSwingDuration' in text \
            or 'soar$onGetModifiedSwingDuration' in text:
        return False
    text = insert_import(text, 'net.wurstclient.hack.HackList')
    text = insert_import(
        text,
        'org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable')
    if version == '26.3':
        if 'SwingAnimation' not in text:
            text = insert_import(
                text, 'net.minecraft.world.item.component.SwingAnimation')
        code = r'''
	@Inject(method = "getModifiedSwingDuration(Lnet/minecraft/world/item/component/SwingAnimation;)I",
		at = @At("HEAD"), cancellable = true)
	private void soar$onGetModifiedSwingDuration(SwingAnimation animation,
		CallbackInfoReturnable<Integer> cir)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax != null && hax.slowSwingHack.isEnabled())
			cir.setReturnValue(hax.slowSwingHack.getDelay());
	}
'''
    else:
        code = r'''
	@Inject(method = "getCurrentSwingDuration()I", at = @At("HEAD"),
		cancellable = true)
	private void soar$onGetCurrentSwingDuration(
		CallbackInfoReturnable<Integer> cir)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax != null && hax.slowSwingHack.isEnabled())
			cir.setReturnValue(hax.slowSwingHack.getDelay());
	}
'''
    text = insert_before_final_brace(text, code)
    write(path, text)
    return True


def patch_entity(path):
    text = read(path)
    if 'soar$updateDamageTilt' in text:
        return False
    text = insert_import(text, 'net.wurstclient.hack.HackList')
    if 'net.minecraft.world.phys.Vec3' not in text:
        text = insert_import(text, 'net.minecraft.world.phys.Vec3')
    text = insert_import(text,
        'org.spongepowered.asm.mixin.injection.callback.CallbackInfo')
    code = r'''
	@Inject(method = "setDeltaMovement(DDD)V", at = @At("HEAD"))
	private void soar$updateDamageTilt(double x, double y, double z,
		CallbackInfo ci)
	{
		net.minecraft.world.entity.Entity self =
			(net.minecraft.world.entity.Entity)(Object)this;
		if(!(self instanceof net.minecraft.world.entity.player.Player player)
			|| player != WurstClient.MC.player)
			return;

		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.damageTiltHack.isEnabled())
			return;

		Vec3 motion = player.getDeltaMovement();
		float hurtDir = (float)(Math.toDegrees(
			Math.atan2(motion.z - z, motion.x - x)) - player.getYRot());
		if(Float.isFinite(hurtDir))
			player.animateHurt(hurtDir);
	}
'''
    text = insert_before_final_brace(text, code)
    write(path, text)
    return True


def patch_held_item(path, version):
    text = read(path)
    if 'soar$applyCustomHeldItemTransform' in text:
        return False
    for imp in ('com.mojang.blaze3d.vertex.PoseStack',
                'net.minecraft.world.item.ItemStack',
                'org.spongepowered.asm.mixin.injection.Inject',
                'org.spongepowered.asm.mixin.injection.callback.CallbackInfo',
                'net.wurstclient.hack.HackList'):
        text = insert_import(text, imp)

    if version == '26.3':
        text = insert_import(text,
            'net.minecraft.client.renderer.SubmitNodeCollector')
        method = 'submitArmWithItem'
        args = r'''		net.minecraft.client.renderer.state.level.PlayerRenderState playerState,
		net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState handState,
		float tickDelta, float pitch, InteractionHand hand, float swingProgress,
		ItemStack item, float equipProgress, PoseStack matrices,
		SubmitNodeCollector collector, int light, CallbackInfo ci'''
    else:
        collector = 'MultiBufferSource' if version in (
            '1.20.1', '1.20.2', '1.20.3', '1.20.4', '1.20.5', '1.20.6', '1.21',
            '1.21.1', '1.21.2', '1.21.3', '1.21.4', '1.21.5', '1.21.6',
            '1.21.7', '1.21.8') else 'SubmitNodeCollector'
        if collector == 'MultiBufferSource':
            text = insert_import(text,
                'net.minecraft.client.renderer.MultiBufferSource')
        else:
            text = insert_import(text,
                'net.minecraft.client.renderer.SubmitNodeCollector')
        method = 'submitArmWithItem' if version == '26.2' \
            else 'renderArmWithItem'
        args = r'''		AbstractClientPlayer player, float tickDelta, float pitch,
		InteractionHand hand, float swingProgress, ItemStack item,
		float equipProgress, PoseStack matrices, %s collector, int light,
		CallbackInfo ci''' % collector

    code = '''
	@Inject(method = "%s", at = @At("HEAD"))
	private void soar$applyCustomHeldItemTransform(
%s)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.customHeldItemsHack.isEnabled())
			return;

		var custom = hax.customHeldItemsHack;
		matrices.translate(custom.getX(), custom.getY(), custom.getZ());
		matrices.scale(custom.getScaleX(), custom.getScaleY(),
			custom.getScaleZ());
	}
''' % (method, args)
    text = insert_before_final_brace(text, code)
    write(path, text)
    return True


SKIN_OLD = '''package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.wurstclient.WurstClient;

@Mixin(SkinManager.class)
public abstract class PlayerSkinProviderMixin
{
	@Inject(
		method = "getInsecureSkin(Lcom/mojang/authlib/GameProfile;)Lnet/minecraft/client/resources/PlayerSkin;",
		at = @At("HEAD"),
		cancellable = true)
	private void soar$protectSkin(GameProfile profile,
		CallbackInfoReturnable<PlayerSkin> cir)
	{
		if(WurstClient.MC.player == null
			|| WurstClient.INSTANCE.getHax() == null
			|| !WurstClient.INSTANCE.getHax().skinProtectHack.isEnabled())
			return;

		if(profile.getId().equals(WurstClient.MC.player.getUUID()))
			cir.setReturnValue(DefaultPlayerSkin.get(profile));
	}
}
'''

SKIN_NEW = '''package net.wurstclient.mixin;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.authlib.GameProfile;

import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.world.entity.player.PlayerSkin;
import net.wurstclient.WurstClient;

@Mixin(SkinManager.class)
public abstract class PlayerSkinProviderMixin
{
	@Inject(
		method = "createLookup(Lcom/mojang/authlib/GameProfile;Z)Ljava/util/function/Supplier;",
		at = @At("HEAD"),
		cancellable = true)
	private void soar$protectSkin(GameProfile profile, boolean secure,
		CallbackInfoReturnable<Supplier<PlayerSkin>> cir)
	{
		if(WurstClient.MC.player == null
			|| WurstClient.INSTANCE.getHax() == null
			|| !WurstClient.INSTANCE.getHax().skinProtectHack.isEnabled())
			return;

		if(profile.id().equals(WurstClient.MC.player.getUUID()))
			cir.setReturnValue(() -> DefaultPlayerSkin.get(profile));
	}
}
'''


def patch_skin(path, version):
    text = read(path)
    if 'soar$protectSkin' in text:
        return False
    write(path, SKIN_NEW if version not in ('1.20.2', '1.20.3', '1.20.4',
                                           '1.20.5', '1.20.6', '1.21',
                                           '1.21.1', '1.21.2', '1.21.3',
                                           '1.21.4', '1.21.5', '1.21.6',
                                           '1.21.7', '1.21.8') else SKIN_OLD)
    return True


def patch_camera(path):
    text = read(path)
    if 'clearWaterHack' in text:
        return False
    old = 'WurstClient.INSTANCE.getHax().noOverlayHack.isEnabled()'
    new = ('(WurstClient.INSTANCE.getHax().noOverlayHack.isEnabled()'
           ' || WurstClient.INSTANCE.getHax().clearWaterHack.isEnabled())')
    if old not in text:
        return False
    write(path, text.replace(old, new))
    return True


FIRE_PATTERN = re.compile(
    r'return original - WurstClient\.INSTANCE\.getHax\(\)\.noFireOverlayHack'
    r'\s*\.getOverlayOffset\(\);')


def patch_overlay(path):
    text = read(path)
    changed = False
    if 'noOverlayHack.isEnabled()' in text and 'clearWaterHack' not in text:
        old = 'WurstClient.INSTANCE.getHax().noOverlayHack.isEnabled()'
        new = ('(WurstClient.INSTANCE.getHax().noOverlayHack.isEnabled()'
               ' || WurstClient.INSTANCE.getHax().clearWaterHack.isEnabled())')
        text = text.replace(old, new)
        changed = True
    if 'noFireOverlayHack' in text and 'isHideFire' not in text:
        text = FIRE_PATTERN.sub(
            'return WurstClient.INSTANCE.getHax().overlayEditorHack.isHideFire()\n'
            '\t\t\t? original - 1000F\n'
            '\t\t\t: original - WurstClient.INSTANCE.getHax().noFireOverlayHack.getOverlayOffset();',
            text)
        changed = True
    if changed:
        write(path, text)
    return changed


def patch_pumpkin(path):
    text = read(path)
    if 'noPumpkinHack' not in text or 'overlayEditorHack' in text:
        return False
    old = '&& hax.noPumpkinHack.isEnabled())'
    new = ('&& (hax.noPumpkinHack.isEnabled()'
           ' || hax.overlayEditorHack.isHidePumpkin()))')
    if old not in text:
        return False
    write(path, text.replace(old, new))
    return True


def patch_project(project):
    root = project / 'src/main/java/net/wurstclient/mixin'
    if not root.exists():
        return {'no_mixin_dir': 1}
    version = version_of(project)
    stats = {}

    def do(name, fn, *args):
        path = root / (name + '.java')
        if not path.exists():
            stats['missing_' + name] = stats.get('missing_' + name, 0) + 1
            return
        if fn(path, *args):
            stats[name] = stats.get(name, 0) + 1

    do('MinecraftClientMixin', patch_minecraft)
    do('LivingEntityMixin', patch_living_entity, version)
    do('EntityMixin', patch_entity)
    do('HeldItemRendererMixin', patch_held_item, version)
    do('PlayerSkinProviderMixin', patch_skin, version)
    do('CameraMixin', patch_camera)

    for name in ('InGameOverlayRendererMixin', 'ScreenEffectRendererMixin'):
        path = root / (name + '.java')
        if path.exists() and patch_overlay(path):
            stats[name] = stats.get(name, 0) + 1

    for name in ('GuiMixin', 'IngameHudMixin'):
        path = root / (name + '.java')
        if path.exists() and patch_pumpkin(path):
            stats[name] = stats.get(name, 0) + 1

    return stats


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', nargs='*')
    args = ap.parse_args()

    if args.only:
        projects = [Path(p).resolve() for p in args.only]
    else:
        projects = [p for p in project_dirs()
                    if p.resolve() != REPO.resolve()]

    total = {}
    for project in projects:
        stats = patch_project(project)
        print(str(project.relative_to(REPO)), stats)
        for key, value in stats.items():
            total[key] = total.get(key, 0) + value
    print('total', total)


if __name__ == '__main__':
    main()
