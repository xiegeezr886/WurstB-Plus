/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin.xray;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.wurstclient.WurstClient;
import net.wurstclient.hacks.XRayHack;

@Mixin(LiquidBlockRenderer.class)
public class LiquidBlockRendererMixin
{
	@Unique
	private static final ThreadLocal<Float> currentOpacity =
		ThreadLocal.withInitial(() -> 1F);

	/**
	 * Hides and shows fluids when using X-Ray without Sodium installed.
	 */
	@WrapOperation(
		method = "tesselate(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;isFaceOccludedByNeighbor(Lnet/minecraft/core/Direction;FLnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean modifyIsFaceOccludedByNeighbor(Direction side, float height,
		BlockState neighborState, Operation<Boolean> original,
		BlockAndTintGetter world, BlockPos pos, VertexConsumer vertexConsumer,
		BlockState blockState, FluidState fluidState)
	{
		XRayHack xray = WurstClient.INSTANCE.getHax().xRayHack;

		// Note: the null BlockPos is here to skip the "exposed only" check
		Boolean shouldDrawSide = xray.shouldDrawSide(blockState, null);

		if(!xray.isOpacityMode() || xray.isVisible(blockState.getBlock(), pos))
			currentOpacity.set(1F);
		else
			currentOpacity.set(xray.getOpacityFloat());

		if(shouldDrawSide != null)
			return !shouldDrawSide;

		return original.call(side, height, neighborState);
	}

	/**
	 * Modifies opacity of fluids when using X-Ray without Sodium installed.
	 *
	 * <p>
	 * Forge's LiquidBlockRenderer patch turns the hardcoded 1F alpha into an
	 * extra trailing float parameter, so the constant injection used on
	 * Fabric cannot match here. Slot 11 is that alpha parameter.
	 */
	@ModifyVariable(
		method = "vertex(Lcom/mojang/blaze3d/vertex/VertexConsumer;FFFFFFFFIF)V",
		at = @At("HEAD"),
		argsOnly = true,
		index = 11)
	private float modifyOpacity(float original)
	{
		return currentOpacity.get();
	}
}
