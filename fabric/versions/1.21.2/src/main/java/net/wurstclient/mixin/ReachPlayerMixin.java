/* Adapted from Meteor Client. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class ReachPlayerMixin
{
	@ModifyReturnValue(method = "blockInteractionRange()D", at = @At("RETURN"))
	private double wurstBlockReach(double original)
	{
		if((Object)this != Minecraft.getInstance().player)
			return original;
		var hax = WurstClient.INSTANCE.getHax();
		return hax == null ? original : hax.reachHack.changeBlockRange(original);
	}

	@ModifyReturnValue(method = "entityInteractionRange()D", at = @At("RETURN"))
	private double wurstEntityReach(double original)
	{
		if((Object)this != Minecraft.getInstance().player)
			return original;
		var hax = WurstClient.INSTANCE.getHax();
		return hax == null ? original : hax.reachHack.changeEntityRange(original);
	}
}
