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

import net.minecraft.client.renderer.block.LiquidBlockRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.ShouldDrawSideListener.ShouldDrawSideEvent;

/**
 * 让 X-Ray 在不装 Sodium 时也能显示/隐藏流体。
 *
 * <p>
 * 1.21.5 及更早版本注入的是 {@code isFaceOccludedByNeighbor}，但 1.21.7 的
 * {@code LiquidBlockRenderer} 只剩 {@code shouldRenderFace} 与
 * {@code tesselate}（已用 javap 核实），所以改为在 {@code tesselate} 内部
 * 包裹 {@code shouldRenderFace} 这个静态调用。
 *
 * <p>
 * {@code shouldRenderFace} 是静态方法、拿不到方块坐标，因此事件里的 pos 传
 * {@code null}——这与官方 Wurst 的做法一致（其 1.21.7 的 FluidRendererMixin
 * 同样传 null）。{@code ShouldDrawSideEvent} 只对 state 做非空校验，
 * {@code XRayHack} 也能接受 null pos。
 */
@Mixin(LiquidBlockRenderer.class)
public class FluidRendererMixin
{
	@WrapOperation(
		method = "tesselate(Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/material/FluidState;)V",
		at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/LiquidBlockRenderer;shouldRenderFace(Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)Z"))
	private boolean wurst$onShouldRenderFace(
		net.minecraft.world.level.material.FluidState fluidState,
		BlockState neighborState, Direction side,
		net.minecraft.world.level.material.FluidState neighborFluidState,
		Operation<Boolean> original,
		@com.llamalad7.mixinextras.sugar.Local(argsOnly = true) BlockState state)
	{
		ShouldDrawSideEvent event = new ShouldDrawSideEvent(state, null);
		EventManager.fire(event);
		
		if(event.isRendered() != null)
			return event.isRendered();
		
		return original.call(fluidState, neighborState, side,
			neighborFluidState);
	}
}