/* SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class CollisionsMovementMixin
{
	@ModifyVariable(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Vec3 wurstLimitUnloadedMovement(Vec3 movement)
	{
		var hax = WurstClient.INSTANCE.getHax();
		return hax == null ? movement : hax.collisionsHack
			.limitMovement((Entity)(Object)this, movement);
	}
}
