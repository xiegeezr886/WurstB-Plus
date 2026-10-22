/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.mixin;

import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.wurstclient.event.EventManager;
import net.wurstclient.events.RenderListener.RenderEvent;
import net.wurstclient.util.RenderUtils;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin
{
	@Shadow
	@Final
	private SubmitNodeStorage submitNodeStorage;

	/**
	 * Vulkan keeps late world-space draws out of sync with the terrain pass.
	 * Submit Wurst's world geometry while the level's node storage is still
	 * open, so it is drawn by the same feature pass as the world. The feature
	 * pass applies the frame's view rotation itself, so the event PoseStack
	 * must stay untransformed.
	 */
	@Inject(
		method = "submitFeatures(Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;Z)V",
		at = @At("RETURN"))
	private void onSubmitFeatures(LevelRenderState levelRenderState,
		SubmitNodeCollector collector, boolean renderOutline, CallbackInfo ci)
	{
		if(!RenderUtils.isVulkanBackend())
			return;

		RenderUtils.setSubmitNodeStorage(submitNodeStorage);
		RenderUtils.beginWorldNodePass();
		try
		{
			RenderEvent event = new RenderEvent(new PoseStack(),
				levelRenderState.worldPartialTicks,
				levelRenderState.cameraRenderState.projectionMatrix);
			EventManager.fire(event);
		}finally
		{
			RenderUtils.endWorldNodePass();
		}
	}

	// 26.3 的 render 去掉了 DeltaTracker 与 modelView 矩阵两个参数，末尾多了一个 boolean。
	// 矩阵改从 CameraRenderState.viewRotationMatrix 取，partialTick 走 Minecraft.getDeltaTracker()。
	@Inject(
		method = "render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lorg/joml/Vector4f;ZZ)V",
		at = @At("RETURN"))
	private void onRender(GraphicsResourceAllocator allocator,
		boolean renderBlockOutline, CameraRenderState cameraState,
		GpuBufferSlice gpuBufferSlice, Vector4f vector4f,
		boolean shouldRenderSky, boolean lastFlag, CallbackInfo ci)
	{
		// Vulkan already fired the event inside submitFeatures.
		if(RenderUtils.isVulkanBackend())
			return;

		RenderUtils.setSubmitNodeStorage(submitNodeStorage);
		PoseStack matrixStack = new PoseStack();
		matrixStack.mulPose(cameraState.viewRotationMatrix);
		float tickProgress = Minecraft.getInstance().getDeltaTracker()
			.getGameTimeDeltaPartialTick(false);
		RenderEvent event = new RenderEvent(matrixStack, tickProgress,
			cameraState.projectionMatrix);
		EventManager.fire(event);
	}
}
