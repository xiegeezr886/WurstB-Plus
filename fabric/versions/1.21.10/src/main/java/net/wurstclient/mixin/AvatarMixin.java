package net.wurstclient.mixin;

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
