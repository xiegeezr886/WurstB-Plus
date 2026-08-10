/*
 * KeepSprint attack handling follows the GPL-3.0 LiquidBounce/FDP approach,
 * adapted for Forge 1.20.1 Mojmap.
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.hack.HackList;
import net.wurstclient.util.KeepSprintPolicy;

@Mixin(Player.class)
public abstract class PlayerMixin
{
	@Redirect(method = "causeExtraKnockback(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/damagesource/DamageSource;FZ)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 keepSprintMotion(Vec3 velocity, double x, double y, double z)
	{
		boolean keepSprint = isKeepSprintActive();
		return velocity.multiply(
			KeepSprintPolicy.attackMotionMultiplier(x, keepSprint), y,
			KeepSprintPolicy.attackMotionMultiplier(z, keepSprint));
	}

	@Redirect(method = "causeExtraKnockback(Lnet/minecraft/world/entity/Entity;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/damagesource/DamageSource;FZ)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
	private void keepSprintState(Player instance, boolean sprinting)
	{
		if(KeepSprintPolicy.shouldApplySprintChange(isKeepSprintActive(),
			sprinting))
			instance.setSprinting(sprinting);
	}

	private boolean isKeepSprintActive()
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		return hax != null && hax.keepSprintHack
			.shouldKeepSprint((Player)(Object)this);
	}

}
