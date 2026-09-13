/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin.noshieldoverlay;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.wurstclient.WurstClient;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin
{
	private static final String RENDER_ARM_WITH_ITEM =
		"renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V";
	
	/**
	 * Lowers the shield (including custom shield items from datapacks) when
	 * blocking if NoShieldOverlay is enabled.
	 */
	@Inject(
		method = RENDER_ARM_WITH_ITEM,
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
			ordinal = 4))
	private void onRenderArmWithItemBlocking(AbstractClientPlayer player,
		float tickProgress, float pitch, InteractionHand hand,
		float swingProgress, ItemStack item, float equipProgress,
		PoseStack matrices, MultiBufferSource.BufferSource buffers, int light,
		CallbackInfo ci)
	{
		// Check if item has block animation component
		if(item.getUseAnimation() != ItemUseAnimation.BLOCK)
			return;
		
		// Lower the shield
		WurstClient.INSTANCE.getHax().noShieldOverlayHack
			.adjustShieldPosition(matrices, true);
	}
	
	/**
	 * Lowers the shield (including custom shield items from datapacks) when
	 * NOT blocking if NoShieldOverlay is enabled.
	 */
	@Inject(
		method = RENDER_ARM_WITH_ITEM,
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmAttackTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V",
			ordinal = 1))
	private void onRenderArmWithItemNotBlocking(AbstractClientPlayer player,
		float tickProgress, float pitch, InteractionHand hand,
		float swingProgress, ItemStack item, float equipProgress,
		PoseStack matrices, MultiBufferSource.BufferSource buffers, int light,
		CallbackInfo ci)
	{
		// Check if item has block animation component
		if(item.getUseAnimation() != ItemUseAnimation.BLOCK)
			return;
		
		// Lower the shield
		WurstClient.INSTANCE.getHax().noShieldOverlayHack
			.adjustShieldPosition(matrices, false);
	}
}
