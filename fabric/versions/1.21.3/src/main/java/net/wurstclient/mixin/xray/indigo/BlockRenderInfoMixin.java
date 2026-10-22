/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin.xray.indigo;

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

@Pseudo
@Mixin(
	targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo",
	remap = false)
public abstract class BlockRenderInfoMixin
{
	@Shadow
	public BlockPos blockPos;
	@Shadow
	public BlockState blockState;

	/**
	 * Indigo 1.21.2-1.21.3 calls this method instead of shouldDrawSide.
	 */
	@Inject(method = "shouldDrawFace",
		at = @At("HEAD"),
		require = 0,
		cancellable = true)
	private void onShouldDrawFace(Direction face,
		CallbackInfoReturnable<Boolean> cir)
	{
		ShouldDrawSideEvent event =
			new ShouldDrawSideEvent(blockState, blockPos);
		EventManager.fire(event);

		if(event.isRendered() != null)
			cir.setReturnValue(event.isRendered());
	}
}