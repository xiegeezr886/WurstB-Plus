/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin.noshieldoverlay;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.wurstclient.WurstClient;

/**
 * 26.3 renamed {@code ItemInHandRenderer} to
 * {@code FirstPersonHandsAndItemsRenderer} and reshaped
 * {@code submitArmWithItem}: it now takes the extracted {@link PlayerRenderState}
 * and {@link FirstPersonHandsAndItemsRenderState} instead of the live player and
 * a dozen scalars.
 *
 * <p>Both injection points were re-derived from the 26.3 bytecode rather than
 * assumed, because the old second anchor no longer exists:
 * <ul>
 * <li>the "is blocking" hook keeps {@code ItemStack.getUseAnimation()}: it is
 * still called exactly once, in the general (non-crossbow) branch, immediately
 * before the {@code switch} whose {@code BLOCK} case transforms the shield.
 * <li>the old "not blocking" hook targeted {@code ItemStack.getSwingAnimation()},
 * which 26.3 no longer calls here at all. Its replacement is the
 * {@code AvatarRenderState.currentSwing} field read: the only occurrence in the
 * method, and in the same else-branch position — right after
 * {@code applyItemArmTransform} — where the old anchor sat.
 * </ul>
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererMixin
{
	private static final String SUBMIT_ARM_WITH_ITEM =
		"submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;"
			+ "Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;"
			+ "FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;F"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";
	
	/**
	 * Lowers the shield (including custom shield items from datapacks) when
	 * blocking if NoShieldOverlay is enabled.
	 */
	@Inject(method = SUBMIT_ARM_WITH_ITEM,
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()"
				+ "Lnet/minecraft/world/item/ItemUseAnimation;",
			shift = At.Shift.AFTER))
	private void onRenderArmWithItemBlocking(PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack,
		float inverseArmHeight, PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci)
	{
		if(itemStack.getUseAnimation() != ItemUseAnimation.BLOCK)
			return;
		
		WurstClient.INSTANCE.getHax().noShieldOverlayHack
			.adjustShieldPosition(poseStack, true);
	}
	
	/**
	 * Lowers the shield (including custom shield items from datapacks) when
	 * NOT blocking if NoShieldOverlay is enabled.
	 */
	@Inject(method = SUBMIT_ARM_WITH_ITEM,
		at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;"
				+ "currentSwing:Lnet/minecraft/world/entity/LivingEntity$SwingDescription;",
			opcode = Opcodes.GETFIELD))
	private void onRenderArmWithItemNotBlocking(PlayerRenderState playerState,
		FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack,
		float inverseArmHeight, PoseStack poseStack,
		SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci)
	{
		if(itemStack.getUseAnimation() != ItemUseAnimation.BLOCK)
			return;
		
		WurstClient.INSTANCE.getHax().noShieldOverlayHack
			.adjustShieldPosition(poseStack, false);
	}
}
