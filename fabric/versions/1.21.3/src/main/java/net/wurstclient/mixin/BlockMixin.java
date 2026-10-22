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
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.WurstClient;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.ShouldDrawSideListener.ShouldDrawSideEvent;
import net.wurstclient.hack.HackList;

@Mixin(Block.class)
public abstract class BlockMixin implements ItemLike
{
	@Inject(method = "getSpeedFactor()F", at = @At("HEAD"), cancellable = true)
	private void onGetVelocityMultiplier(CallbackInfoReturnable<Float> cir)
	{
		HackList hax = WurstClient.INSTANCE.getHax();
		if(hax == null || !hax.noSlowdownHack.isEnabled())
			return;

		if(cir.getReturnValueF() < 1)
			cir.setReturnValue(1F);
	}

	/**
	 * X-Ray culling hook for versions whose renderer calls
	 * Block.shouldRenderFace directly. Both overloads are targeted because
	 * the position-aware one is not present in every 1.21.x build.
	 */
	@Inject(at = @At("HEAD"),
		method = "shouldRenderFace(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
		cancellable = true,
		require = 0)
	private static void onShouldDrawSide3Arg(BlockState state,
		BlockState neighborState, Direction direction,
		CallbackInfoReturnable<Boolean> cir)
	{
		fireShouldDrawSide(state, null, cir);
	}

	@Inject(at = @At("HEAD"),
		method = "shouldRenderFace(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;)Z",
		cancellable = true,
		require = 0)
	private static void onShouldDrawSide5Arg(BlockGetter level, BlockPos pos,
		BlockState state, BlockState neighborState, Direction direction,
		CallbackInfoReturnable<Boolean> cir)
	{
		fireShouldDrawSide(state, pos, cir);
	}

	private static void fireShouldDrawSide(BlockState state, BlockPos pos,
		CallbackInfoReturnable<Boolean> cir)
	{
		ShouldDrawSideEvent event = new ShouldDrawSideEvent(state, pos);
		EventManager.fire(event);

		if(event.isRendered() != null)
			cir.setReturnValue(event.isRendered());
	}
}