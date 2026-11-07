/* Adapted from Meteor Client. Copyright (c) Meteor Development. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.TridentItem;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TridentItem.class)
public abstract class TridentBoostMixin
{
	@Redirect(method = "releaseUsing", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;push(DDD)V"))
	private void wurstRiptideVelocity(Player player, double x, double y, double z)
	{
		var hax = WurstClient.INSTANCE.getHax();
		double factor = player == WurstClient.MC.player && hax != null
			? hax.tridentBoostHack.getMultiplier() : 1;
		player.push(x * factor, y * factor, z * factor);
	}

	@Redirect(method = {"use", "releaseUsing"}, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/entity/player/Player;isInWaterOrRain()Z"), require = 2)
	private boolean wurstRiptideWater(Player player)
	{
		var hax = WurstClient.INSTANCE.getHax();
		return player.isInWaterOrRain() || player == WurstClient.MC.player
			&& hax != null && hax.tridentBoostHack.allowOutOfWater();
	}
}
