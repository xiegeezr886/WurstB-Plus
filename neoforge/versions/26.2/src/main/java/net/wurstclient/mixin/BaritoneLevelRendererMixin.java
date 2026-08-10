/*
 * Copyright (c) 2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.RenderEvent;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

@Mixin(LevelRenderer.class)
public class BaritoneLevelRendererMixin
{
	@Inject(
		method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
		at = @At("RETURN"))
	private void wurst$renderBaritone(GraphicsResourceAllocator allocator,
		DeltaTracker deltaTracker, boolean outline, CameraRenderState camera,
		Matrix4fc modelViewMatrix, GpuBufferSlice fog, Vector4f fogColor,
		boolean sky, CallbackInfo ci)
	{
		for(IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones())
		{
			PoseStack poseStack = new PoseStack();
			poseStack.mulPose(modelViewMatrix);
			baritone.getGameEventHandler().onRenderPass(new RenderEvent(
				deltaTracker.getGameTimeDeltaPartialTick(false), poseStack,
				camera.projectionMatrix));
		}
	}
}

