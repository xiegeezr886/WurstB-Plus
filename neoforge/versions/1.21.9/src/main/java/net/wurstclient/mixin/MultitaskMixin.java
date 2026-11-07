/* Adapted from Meteor Client. Copyright (c) Meteor Development. SPDX-License-Identifier: GPL-3.0 */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MultitaskMixin
{
	@ModifyExpressionValue(method = "startUseItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"))
	private boolean wurstUseWhileMining(boolean original)
	{
		var hax = WurstClient.INSTANCE.getHax();
		return original && (hax == null || !hax.multitaskHack.isEnabled());
	}

	@ModifyExpressionValue(method = "continueAttack", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
	private boolean wurstMineWhileUsing(boolean original)
	{
		var hax = WurstClient.INSTANCE.getHax();
		return original && (hax == null || !hax.multitaskHack.isEnabled());
	}

	@ModifyExpressionValue(method = "handleKeybinds", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0))
	private boolean wurstAttackWhileUsing(boolean original)
	{
		var hax = WurstClient.INSTANCE.getHax();
		return original && (hax == null || !hax.multitaskHack.attackingEntities());
	}

	@Inject(method = "handleKeybinds", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0))
	private void wurstPreserveUseRelease(CallbackInfo ci)
	{
		Minecraft mc = (Minecraft)(Object)this;
		var hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.multitaskHack.attackingEntities()
			|| mc.player == null || mc.gameMode == null || !mc.player.isUsingItem())
			return;
		if(!mc.options.keyUse.isDown())
			mc.gameMode.releaseUsingItem(mc.player);
		while(mc.options.keyUse.consumeClick())
		{
			// Vanilla normally consumes these while the item is already in use.
		}
	}
}
