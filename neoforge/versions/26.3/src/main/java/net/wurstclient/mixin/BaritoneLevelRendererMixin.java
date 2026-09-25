/*
 * Copyright (c) 2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.RenderEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;

@Mixin(LevelRenderer.class)
public class BaritoneLevelRendererMixin
{
	// 26.3 的 render 去掉了 DeltaTracker 与 modelView 矩阵两个参数，末尾多了一个 boolean。
	// 矩阵改从 CameraRenderState.viewRotationMatrix 取，partialTick 走 Minecraft.getDeltaTracker()。
	@Inject(
		method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
		at = @At("RETURN"))
	private void wurst$renderBaritone(GraphicsResourceAllocator allocator,
		boolean renderBlockOutline, CameraRenderState camera,
		GpuBufferSlice fog, Vector4f fogColor, boolean shouldRenderSky,
		boolean lastFlag, CallbackInfo ci)
	{
		for(IBaritone baritone : BaritoneAPI.getProvider().getAllBaritones())
		{
			PoseStack poseStack = new PoseStack();
			poseStack.mulPose(camera.viewRotationMatrix);
			baritone.getGameEventHandler().onRenderPass(new RenderEvent(
				Minecraft.getInstance().getDeltaTracker()
					.getGameTimeDeltaPartialTick(false),
				poseStack, camera.projectionMatrix));
		}
	}
}
