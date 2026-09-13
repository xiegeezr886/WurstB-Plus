/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;

import org.joml.Matrix4fStack;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.MeshData.DrawState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;

import net.minecraft.client.renderer.RenderType;

/**
 * An abstraction of Minecraft 1.21.5's new {@code GpuBuffer} system that makes
 * working with it as easy as {@code VertexBuffer} was.
 *
 * <p>
 * 1.21.5 has no {@code RenderSystem.getDynamicUniforms()} or
 * {@code bindDefaultUniforms()} yet: {@code GpuDevice.createRenderPass()}
 * captures ModelViewMat, ProjMat, ColorModulator and the fog/line uniforms
 * straight from {@code RenderSystem} state. The view transform is therefore
 * applied by pushing the given PoseStack onto
 * {@code RenderSystem.getModelViewStack()} for the duration of the draw.
 */
public final class EasyVertexBuffer implements AutoCloseable
{
	private final RenderSystem.AutoStorageIndexBuffer shapeIndexBuffer;
	private final GpuBuffer vertexBuffer;
	private final int indexCount;
	
	/**
	 * Drop-in replacement for {@code VertexBuffer.createAndUpload()}.
	 */
	public static EasyVertexBuffer createAndUpload(Mode drawMode,
		VertexFormat format, Consumer<VertexConsumer> callback)
	{
		BufferBuilder bufferBuilder =
			Tesselator.getInstance().begin(drawMode, format);
		callback.accept(bufferBuilder);
		
		MeshData buffer = bufferBuilder.build();
		if(buffer == null)
			return new EasyVertexBuffer();
		
		try(buffer)
		{
			return new EasyVertexBuffer(buffer);
		}
	}
	
	/**
	 * Builds and draws a one-off piece of geometry into the given layer.
	 */
	public static void drawImmediate(PoseStack matrixStack, RenderType layer,
		Mode drawMode, VertexFormat format,
		Consumer<VertexConsumer> callback)
	{
		try(EasyVertexBuffer buffer =
			createAndUpload(drawMode, format, callback))
		{
			buffer.draw(matrixStack, layer);
		}
	}
	
	private EasyVertexBuffer(MeshData buffer)
	{
		DrawState drawParams = buffer.drawState();
		shapeIndexBuffer = RenderSystem.getSequentialBuffer(drawParams.mode());
		indexCount = drawParams.indexCount();
		
		vertexBuffer = drawParams.format()
			.uploadImmediateVertexBuffer(buffer.vertexBuffer());
	}
	
	private EasyVertexBuffer()
	{
		shapeIndexBuffer = null;
		indexCount = 0;
		vertexBuffer = null;
	}
	
	/**
	 * Similar to {@code VertexBuffer.draw(RenderLayer)}, but with a
	 * customizable view matrix. Use this if you need to translate/scale/rotate
	 * the buffer.
	 */
	public void draw(PoseStack matrixStack, RenderType layer)
	{
		draw(matrixStack, layer, () -> {});
	}
	
	public void draw(PoseStack matrixStack, RenderType layer,
		Runnable afterSetup)
	{
		if(vertexBuffer == null)
		{
			afterSetup.run();
			return;
		}
		
		layer.setupRenderState();
		try
		{
			Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
			modelViewStack.pushMatrix();
			modelViewStack.mul(matrixStack.last().pose());
			
			try
			{
				afterSetup.run();
				
				RenderTarget framebuffer = layer.getRenderTarget();
				RenderPipeline pipeline = layer.getRenderPipeline();
				GpuBuffer indexBuffer =
					shapeIndexBuffer.getBuffer(indexCount);
				
				try(RenderPass renderPass = RenderSystem.getDevice()
					.createCommandEncoder().createRenderPass(
						framebuffer.getColorTexture(), OptionalInt.empty(),
						framebuffer.useDepth ? framebuffer.getDepthTexture()
							: null,
						OptionalDouble.empty()))
				{
					renderPass.setPipeline(pipeline);
					renderPass.setVertexBuffer(0, vertexBuffer);
					renderPass.setIndexBuffer(indexBuffer,
						shapeIndexBuffer.type());
					renderPass.drawIndexed(0, indexCount);
				}
			}finally
			{
				modelViewStack.popMatrix();
			}
		}finally
		{
			layer.clearRenderState();
		}
	}
	
	@Override
	public void close()
	{
		if(vertexBuffer != null)
			vertexBuffer.close();
	}
}
