# -*- coding: utf-8 -*-
"""Add ForceMainHand and SnapTap hooks to every project."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs
from patch_soar_mixins import (insert_import, insert_before_final_brace,
                              read, write)


def patch_player(path):
    text = read(path)
    if 'soar$forceMainArm' in text:
        return False

    for imp in ('net.minecraft.world.entity.HumanoidArm',
                'org.spongepowered.asm.mixin.injection.Inject',
                'org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable',
                'net.wurstclient.hack.HackList'):
        text = insert_import(text, imp)

    code = r'''
	@Inject(method = "getMainArm()Lnet/minecraft/world/entity/HumanoidArm;",
		at = @At("HEAD"), cancellable = true)
	private void soar$forceMainArm(
		CallbackInfoReturnable<HumanoidArm> cir)
	{
		Player self = (Player)(Object)this;
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.forceMainHandHack.isEnabled()
			|| self == WurstClient.MC.player)
			return;

		cir.setReturnValue(hax.forceMainHandHack.isRightHand()
			? HumanoidArm.RIGHT : HumanoidArm.LEFT);
	}
'''
    text = insert_before_final_brace(text, code)
    write(path, text)
    return True


def patch_key(path):
    text = read(path)
    if 'soar$snapTapIsDown' in text:
        return False

    for imp in ('org.spongepowered.asm.mixin.Final',
                'org.spongepowered.asm.mixin.injection.At',
                'org.spongepowered.asm.mixin.injection.Inject',
                'org.spongepowered.asm.mixin.injection.callback.CallbackInfo',
                'org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable',
                'net.wurstclient.WurstClient',
                'net.wurstclient.hack.HackList'):
        text = insert_import(text, imp)

    # Fields are inserted before the final brace as well; Java allows fields
    # after methods. Only add them if the mixin does not already shadow them.
    fields = ''
    if 'private boolean isDown' not in text:
        fields += '\n\t@Shadow\n\tprivate boolean isDown;\n'
    if 'private InputConstants.Key defaultKey' not in text:
        fields += '\n\t@Shadow\n\t@Final\n\tprivate InputConstants.Key defaultKey;\n'

    code = fields + r'''
	@Inject(method = "isDown()Z", at = @At("HEAD"), cancellable = true)
	private void soar$snapTapIsDown(CallbackInfoReturnable<Boolean> cir)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.snapTapHack.isEnabled() || !isDown)
			return;

		int code = defaultKey.getValue();
		if(code == InputConstants.KEY_A)
		{
			long right = hax.snapTapHack.getRightPressTime();
			if(right != 0)
				cir.setReturnValue(right <= hax.snapTapHack
					.getLeftPressTime());
		}else if(code == InputConstants.KEY_D)
		{
			long left = hax.snapTapHack.getLeftPressTime();
			if(left != 0)
				cir.setReturnValue(left <= hax.snapTapHack
					.getRightPressTime());
		}else if(code == InputConstants.KEY_W)
		{
			long back = hax.snapTapHack.getBackPressTime();
			if(back != 0)
				cir.setReturnValue(back <= hax.snapTapHack
					.getForwardPressTime());
		}else if(code == InputConstants.KEY_S)
		{
			long forward = hax.snapTapHack.getForwardPressTime();
			if(forward != 0)
				cir.setReturnValue(forward <= hax.snapTapHack
					.getBackPressTime());
		}
	}

	@Inject(method = "setDown(Z)V", at = @At("HEAD"))
	private void soar$snapTapSetDown(boolean pressed, CallbackInfo ci)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.snapTapHack.isEnabled())
			return;

		long now = pressed ? System.currentTimeMillis() : 0;
		int code = defaultKey.getValue();
		if(code == InputConstants.KEY_A)
			hax.snapTapHack.setLeftPressTime(now);
		else if(code == InputConstants.KEY_D)
			hax.snapTapHack.setRightPressTime(now);
		else if(code == InputConstants.KEY_W)
			hax.snapTapHack.setForwardPressTime(now);
		else if(code == InputConstants.KEY_S)
			hax.snapTapHack.setBackPressTime(now);
	}
'''
    text = insert_before_final_brace(text, code)
    write(path, text)
    return True


def patch_project(project):
    mixins = project / 'src/main/java/net/wurstclient/mixin'
    stats = {}
    player = mixins / 'PlayerMixin.java'
    if player.exists() and patch_player(player):
        stats['PlayerMixin'] = 1

    key = None
    for name in ('KeyMappingMixin.java', 'KeyBindingMixin.java'):
        path = mixins / name
        if path.exists():
            key = path
            break
    if key is not None and patch_key(key):
        stats[key.name] = 1
    return stats


def main():
    projects = [p for p in project_dirs()]
    if REPO not in projects:
        projects.append(REPO)
    total = {}
    for project in projects:
        stats = patch_project(project)
        print(str(project.relative_to(REPO)), stats)
        for key, value in stats.items():
            total[key] = total.get(key, 0) + value
    print('total', total)


if __name__ == '__main__':
    main()
