/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.ShouldDrawSideListener.ShouldDrawSideEvent;

/** Sodium 0.8.0 through 0.8.14, including the fluid culling rewrite. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer",
	remap = false)
public class DefaultFluidRendererMixin
{
	// Each Sodium worker owns its renderer. Every render starts a fresh decision;
	// the private visibility helpers below only run within that render call.
	@Unique
	private Boolean wurst$fluidRendered;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void wurst$onRender(CallbackInfo ci,
		@Local(argsOnly = true) BlockState state,
		@Local(argsOnly = true, ordinal = 0) BlockPos pos)
	{
		wurst$fluidRendered = null;
		ShouldDrawSideEvent event = new ShouldDrawSideEvent(state, pos);
		EventManager.fire(event);
		if(Boolean.FALSE.equals(event.isRendered()))
		{
			ci.cancel();
			return;
		}
		wurst$fluidRendered = event.isRendered();
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void wurst$afterRender(CallbackInfo ci)
	{
		wurst$fluidRendered = null;
	}

	// These names cover both sides of Sodium's 0.8 fluid culling rewrite.
	// Absent alternate methods are optional; render itself remains required.
	@Inject(method = {"isSideExposed", "isFullBlockFluidVisible",
		"isFullBlockFluidSelfVisible", "isFullBlockFluidSideVisible",
		"isSideExposedOffset"}, at = @At("HEAD"), cancellable = true,
		require = 0)
	private void wurst$onVisibility(CallbackInfoReturnable<Boolean> cir)
	{
		if(wurst$fluidRendered != null)
			cir.setReturnValue(wurst$fluidRendered);
	}

	@Inject(method = "isFullBlockFluidOccluded", at = @At("HEAD"),
		cancellable = true, require = 0)
	private void wurst$onOccluded(CallbackInfoReturnable<Boolean> cir)
	{
		if(wurst$fluidRendered != null)
			cir.setReturnValue(!wurst$fluidRendered);
	}

	@Inject(method = "getUpFaceExposureByNeighbors", at = @At("HEAD"),
		cancellable = true, require = 0)
	private void wurst$onUpFaceExposure(CallbackInfoReturnable<Integer> cir)
	{
		if(Boolean.TRUE.equals(wurst$fluidRendered))
			// BOTH_EXPOSED is the union of outward (01) and inward (10) bits.
			cir.setReturnValue(0b11);
	}
}
