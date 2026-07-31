/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.wurstclient.WurstClient;

@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin
{
	@ModifyExpressionValue(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/AbstractClientPlayer;isUsingItem()Z"),
		method = "renderArmWithItem")
	private boolean spoofAuraFakeBlock(boolean original)
	{
		return original || getFakeBlockingHand() != null;
	}

	@ModifyExpressionValue(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/AbstractClientPlayer;getUseItemRemainingTicks()I"),
		method = "renderArmWithItem")
	private int spoofAuraFakeBlockTime(int original)
	{
		return getFakeBlockingHand() == null ? original : Integer.MAX_VALUE;
	}

	@ModifyExpressionValue(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/AbstractClientPlayer;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"),
		method = "renderArmWithItem")
	private InteractionHand spoofAuraFakeBlockHand(
		InteractionHand original)
	{
		InteractionHand fake = getFakeBlockingHand();
		return fake == null ? original : fake;
	}

	private InteractionHand getFakeBlockingHand()
	{
		InteractionHand hand = WurstClient.INSTANCE.getHax().killauraHack
			.getFakeBlockingHand();
		return hand != null ? hand : WurstClient.INSTANCE.getHax().multiAuraHack
			.getFakeBlockingHand();
	}
}
