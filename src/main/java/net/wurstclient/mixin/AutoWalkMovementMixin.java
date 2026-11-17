/* SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LocalPlayer.class)
public abstract class AutoWalkMovementMixin
{
	@ModifyVariable(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private Vec3 wurstWaitForChunks(Vec3 movement)
	{
		var hax = WurstClient.INSTANCE.getHax();
		return hax == null ? movement : hax.autoWalkHack.limitMovement(movement);
	}
}
