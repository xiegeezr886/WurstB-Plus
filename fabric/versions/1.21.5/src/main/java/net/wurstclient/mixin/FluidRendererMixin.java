/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.ShouldDrawSideListener.ShouldDrawSideEvent;

@Mixin(LiquidBlockRenderer.class)
public class FluidRendererMixin
{
	/**
	 * Shows and hides fluids when using X-Ray without Sodium installed.
	 *
	 * <p>
	 * 1.21.2 removed the {@code BlockGetter}/{@code BlockPos} parameters from
	 * {@code isFaceOccludedByNeighbor()}, so the position of the fluid is no
	 * longer available inside it. The call site is wrapped instead, which
	 * still has access to the enclosing {@code tesselate()} parameters.
	 */
	@WrapOperation(
		method = "tesselate(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;isFaceOccludedByNeighbor(Lnet/minecraft/core/Direction;FLnet/minecraft/world/level/block/state/BlockState;)Z"))
	private boolean onIsSideCovered(Direction side, float maxDeviation,
		BlockState neighboringBlockState, Operation<Boolean> original,
		@Local(argsOnly = true) BlockPos pos,
		@Local(argsOnly = true) BlockState state)
	{
		ShouldDrawSideEvent event = new ShouldDrawSideEvent(state, pos);
		EventManager.fire(event);

		if(event.isRendered() != null)
			return !event.isRendered();

		return original.call(side, maxDeviation, neighboringBlockState);
	}
}
