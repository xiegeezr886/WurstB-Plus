/*
 * BreakDelay behavior adapted from Meteor Client.
 * Copyright (c) Meteor Development. SPDX-License-Identifier: GPL-3.0
 */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(MultiPlayerGameMode.class)
public abstract class FastBreakCooldownMixin
{
	@Shadow private int destroyDelay;

	@ModifyConstant(method = {"startDestroyBlock", "continueDestroyBlock"},
		constant = @Constant(intValue = 5), require = 3)
	private int wurstBreakCooldown(int original)
	{
		var hax = WurstClient.INSTANCE.getHax();
		return hax == null ? original : hax.fastBreakHack.getBreakCooldown(original);
	}

	@ModifyExpressionValue(method = "continueDestroyBlock",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroyProgress(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"))
	private float wurstPreventInstantChain(float original)
	{
		var hax = WurstClient.INSTANCE.getHax();
		if(hax != null && original >= 1 && hax.fastBreakHack.shouldPreventInstantBreak())
		{
			destroyDelay = hax.fastBreakHack.getBreakCooldown(destroyDelay);
			return 0;
		}
		return original;
	}
}
