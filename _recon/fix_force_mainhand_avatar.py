# -*- coding: utf-8 -*-
"""Move ForceMainHand's getMainArm hook to Avatar on 1.21.9+."""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from add_upstream_hacks import REPO, project_dirs

TARGETS = {'1.21.9', '1.21.10', '1.21.11', '26.1', '26.1.1',
           '26.1.2', '26.2', '26.3'}

AVATAR_MIXIN = '''package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.HackList;

@Mixin(Avatar.class)
public abstract class AvatarMixin
{
	@Inject(method = "getMainArm()Lnet/minecraft/world/entity/HumanoidArm;",
		at = @At("HEAD"), cancellable = true)
	private void soar$forceMainArm(
		CallbackInfoReturnable<HumanoidArm> cir)
	{
		Object self = (Object)this;
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.forceMainHandHack.isEnabled()
			|| !(self instanceof Player player)
			|| player == WurstClient.MC.player)
			return;

		cir.setReturnValue(hax.forceMainHandHack.isRightHand()
			? HumanoidArm.RIGHT : HumanoidArm.LEFT);
	}
}
'''

BLOCK = re.compile(
    r'\n\t@Inject\(method = "getMainArm\(\)Lnet/minecraft/world/entity/HumanoidArm;",'
    r'.*?\n\t\}\n', re.S)


def version_of(project):
    parts = project.parts
    try:
        return parts[parts.index('versions') + 1]
    except ValueError:
        return ''


def add_config(project):
    for name in ('wurstpenguin.mixins.json', 'wurst.mixins.json'):
        path = project / 'src/main/resources' / name
        if not path.exists():
            continue
        text = path.read_text(encoding='utf-8')
        if '"AvatarMixin"' in text:
            return False
        text = re.sub(r'("client"\s*:\s*\[)',
                      r'\1\n    "AvatarMixin",', text, count=1)
        path.write_text(text, encoding='utf-8', newline='\n')
        return True
    return False


def main():
    root = REPO.resolve()
    stats = {'player_removed': 0, 'avatar': 0, 'config': 0}
    for project in project_dirs():
        version = version_of(project)
        if version not in TARGETS:
            continue
        player = project / 'src/main/java/net/wurstclient/mixin/PlayerMixin.java'
        if player.exists():
            text = player.read_text(encoding='utf-8')
            if 'soar$forceMainArm' in text:
                new = BLOCK.sub('\n', text)
                if new != text:
                    player.write_text(new, encoding='utf-8', newline='\n')
                    stats['player_removed'] += 1

        out = project / 'src/main/java/net/wurstclient/mixin/AvatarMixin.java'
        out.write_text(AVATAR_MIXIN, encoding='utf-8', newline='\n')
        stats['avatar'] += 1
        if add_config(project):
            stats['config'] += 1
    print(stats)


if __name__ == '__main__':
    main()
