/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.world.InteractionHand;
import net.wurstclient.WurstClient;

/**
 * Spoofs a block pose so Killaura/MultiAura's fake blocking renders.
 *
 * <p>26.3 renamed {@code ItemInHandRenderer} to
 * {@code FirstPersonHandsAndItemsRenderer}, and its {@code submitArmWithItem} no
 * longer reads the live {@code AbstractClientPlayer} — it reads an already
 * extracted render state. The three spoofs therefore moved from entity method
 * calls to field reads:
 * <ul>
 * <li>{@code AbstractClientPlayer.isUsingItem()} →
 * {@code AvatarRenderState.isUsingItem}
 * <li>{@code AbstractClientPlayer.getUseItemRemainingTicks()} →
 * {@code FirstPersonHandsAndItemsRenderState.useItemRemainingTicks}
 * <li>{@code AbstractClientPlayer.getUsedItemHand()} →
 * {@code AvatarRenderState.useItemHand}
 * </ul>
 *
 * <p>The ordinals are not guesses: {@code submitArmWithItem} contains two
 * structurally identical "is using item" guards — the first belongs to the
 * crossbow branch, the second is the general branch whose {@code switch} has the
 * {@code BLOCK} case. Only the general one must be spoofed, otherwise a crossbow
 * would be rendered as if it were being used. Disassembling the 26.3 method gives
 * the field-read order, so the general guard is {@code isUsingItem} #1,
 * {@code useItemHand} #1, and {@code useItemRemainingTicks} #2 (its #0/#1 are the
 * crossbow guard and an unrelated duration computation).
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HeldItemRendererMixin
{
	private static final String SUBMIT_ARM_WITH_ITEM =
		"submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;"
			+ "Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;"
			+ "FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;F"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";
	
	@ModifyExpressionValue(method = SUBMIT_ARM_WITH_ITEM,
		at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;"
				+ "isUsingItem:Z",
			opcode = Opcodes.GETFIELD, ordinal = 1))
	private boolean spoofAuraFakeBlock(boolean original)
	{
		return original || getFakeBlockingHand() != null;
	}
	
	@ModifyExpressionValue(method = SUBMIT_ARM_WITH_ITEM,
		at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/state/level/"
				+ "FirstPersonHandsAndItemsRenderState;useItemRemainingTicks:I",
			opcode = Opcodes.GETFIELD, ordinal = 2))
	private int spoofAuraFakeBlockTime(int original)
	{
		return getFakeBlockingHand() == null ? original : Integer.MAX_VALUE;
	}
	
	@ModifyExpressionValue(method = SUBMIT_ARM_WITH_ITEM,
		at = @At(value = "FIELD",
			target = "Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;"
				+ "useItemHand:Lnet/minecraft/world/InteractionHand;",
			opcode = Opcodes.GETFIELD, ordinal = 1))
	private InteractionHand spoofAuraFakeBlockHand(InteractionHand original)
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
