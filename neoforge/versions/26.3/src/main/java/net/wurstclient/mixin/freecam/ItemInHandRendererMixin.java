/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin.freecam;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.wurstclient.WurstClient;

/**
 * Makes the "Hide hand" setting in Freecam work.
 *
 * <p>26.3 renamed {@code ItemInHandRenderer} to
 * {@code FirstPersonHandsAndItemsRenderer} and changed
 * {@code submitHandsWithItems} to take extracted render states instead of the
 * live {@code LocalPlayer}.
 *
 * <p><b>This mixin was never registered in {@code wurst.mixins.json}</b> before
 * this port, so the setting it implements had no effect. It is registered now.
 * Unlike upstream Wurst, it deliberately covers only Freecam: this fork's
 * {@code RemoteViewHack} has no {@code shouldHideHand()} counterpart, so
 * extending the check to RemoteView would be adding a feature rather than
 * porting one.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ItemInHandRendererMixin
{
	@Inject(
		method = "submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;"
			+ "Lnet/minecraft/client/renderer/state/level/PlayerRenderState;"
			+ "Lnet/minecraft/client/renderer/state/level/"
			+ "FirstPersonHandsAndItemsRenderState;)V",
		at = @At("HEAD"),
		cancellable = true)
	private void onRenderHandsWithItems(float tickProgress, PoseStack matrices,
		SubmitNodeCollector entityRenderCommandQueue, PlayerRenderState player,
		FirstPersonHandsAndItemsRenderState handsAndItems, CallbackInfo ci)
	{
		if(WurstClient.INSTANCE.getHax().freecamHack.shouldHideHand())
			ci.cancel();
	}
}
