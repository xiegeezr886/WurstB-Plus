/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.wurstclient.hack.HackList;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
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
		method = "submitArmWithItem")
	private boolean spoofAuraFakeBlock(boolean original)
	{
		return original || getFakeBlockingHand() != null;
	}

	@ModifyExpressionValue(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/AbstractClientPlayer;getUseItemRemainingTicks()I"),
		method = "submitArmWithItem")
	private int spoofAuraFakeBlockTime(int original)
	{
		return getFakeBlockingHand() == null ? original : Integer.MAX_VALUE;
	}

	@ModifyExpressionValue(at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/player/AbstractClientPlayer;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"),
		method = "submitArmWithItem")
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

	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void soar$applyCustomHeldItemTransform(
		AbstractClientPlayer player, float tickDelta, float pitch,
		InteractionHand hand, float swingProgress, ItemStack item,
		float equipProgress, PoseStack matrices, SubmitNodeCollector collector, int light,
		CallbackInfo ci)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.customHeldItemsHack.isEnabled())
			return;

		var custom = hax.customHeldItemsHack;
		matrices.translate(custom.getX(), custom.getY(), custom.getZ());
		matrices.scale(custom.getScaleX(), custom.getScaleY(),
			custom.getScaleZ());
	}

}
