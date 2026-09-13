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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.wurstclient.WurstClient;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin
{
	@Inject(at = @At("HEAD"),
		method = "renderEntity(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;)V",
		cancellable = true)
	private void onRenderEntity(Entity entity, double cameraX, double cameraY,
		double cameraZ, float partialTicks, PoseStack poseStack,
		MultiBufferSource buffers, CallbackInfo ci)
	{
		if(WurstClient.INSTANCE.getHax().entityCullingHack.isEnabled()
			&& WurstClient.INSTANCE.getHax().entityCullingHack.shouldCull(entity,
				cameraX, cameraY, cameraZ, partialTicks, poseStack))
			ci.cancel();
	}

	@Inject(at = @At("HEAD"),
		method = "doesMobEffectBlockSky(Lnet/minecraft/client/Camera;)Z",
		cancellable = true)
	private void onHasBlindnessOrDarkness(Camera camera,
		CallbackInfoReturnable<Boolean> ci)
	{
		if(WurstClient.INSTANCE.getHax().antiBlindHack.isEnabled())
			ci.setReturnValue(false);
	}
}
