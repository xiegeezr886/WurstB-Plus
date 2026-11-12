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
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.ShouldDrawSideListener.ShouldDrawSideEvent;

/** Sodium 0.8.0 through 0.8.14: intercept before cached or disabled culling. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext",
	remap = false)
public class AbstractBlockRenderContextMixin
{
	@Shadow
	protected BlockState state;
	@Shadow
	protected BlockPos pos;

	@Inject(method = "isFaceCulled", at = @At("HEAD"), cancellable = true)
	private void wurst$onIsFaceCulled(Direction face,
		CallbackInfoReturnable<Boolean> cir)
	{
		// Non-terrain contexts can lack a world position or block state.
		if(state == null || pos == null)
			return;

		ShouldDrawSideEvent event = new ShouldDrawSideEvent(state, pos);
		EventManager.fire(event);
		if(event.isRendered() != null)
			cir.setReturnValue(!event.isRendered());
	}
}
