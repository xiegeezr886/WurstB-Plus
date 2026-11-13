package net.wurstclient.util.render;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

public final class PostEffectQueue
{
	private static final String NAMESPACE = "wurst";
	private final EnumMap<Effect, List<RenderTask>> tasks =
		new EnumMap<>(Effect.class);
	private final EnumMap<Effect, EffectTarget> targets =
		new EnumMap<>(Effect.class);
	private final EnumSet<Effect> failedEffects = EnumSet.noneOf(Effect.class);

	public PostEffectQueue()
	{
		for(Effect effect : Effect.values())
			tasks.put(effect, new ArrayList<>());
	}

	public void queue(Effect effect, RenderTask task)
	{
		RenderSystem.assertOnRenderThread();
		tasks.get(effect).add(task);
	}

	public void flush(PoseStack matrices, float partialTicks)
	{
		RenderSystem.assertOnRenderThread();
		if(isEmpty())
			return;

		Minecraft client = Minecraft.getInstance();
		RenderTarget mainTarget = client.getMainRenderTarget();
		try(RenderScope ignored = RenderScope.capture())
		{
			for(Effect effect : Effect.values())
			{
				List<RenderTask> effectTasks = tasks.get(effect);
				if(effectTasks.isEmpty() || failedEffects.contains(effect))
					continue;
				try
				{
					renderEffect(client, mainTarget, effect, effectTasks,
						matrices, partialTicks);
				}catch(IOException | RuntimeException e)
				{
					failedEffects.add(effect);
					System.err.println("[PostEffect] Disabling " + effect
						+ " after initialization/render failure: " + e);
				}
			}
		}finally
		{
			for(List<RenderTask> effectTasks : tasks.values())
				effectTasks.clear();
			mainTarget.bindWrite(false);
		}
	}

	private void renderEffect(Minecraft client, RenderTarget mainTarget,
		Effect effect, List<RenderTask> effectTasks, PoseStack matrices,
		float partialTicks) throws IOException
	{
		EffectTarget target = getOrCreateTarget(client, effect,
			mainTarget.width, mainTarget.height);
		target.resize(mainTarget.width, mainTarget.height);
		target.renderTarget.setClearColor(0, 0, 0, 0);
		target.renderTarget.clear(Minecraft.ON_OSX);
		target.renderTarget.copyDepthFrom(mainTarget);
		target.renderTarget.bindWrite(false);

		for(RenderTask task : effectTasks)
			try(RenderScope ignored = RenderScope.capture())
			{
				task.render(matrices, partialTicks);
			}

		target.postChain.process(partialTicks);
		mainTarget.bindWrite(false);
		composite(target.renderTarget, mainTarget.width, mainTarget.height);
	}

	private EffectTarget getOrCreateTarget(Minecraft client, Effect effect,
		int width, int height) throws IOException
	{
		EffectTarget target = targets.get(effect);
		if(target != null)
			return target;

		TextureTarget renderTarget = new TextureTarget(width, height, true,
			Minecraft.ON_OSX);
		ResourceLocation shader = ResourceLocation.tryBuild(NAMESPACE,
			"shaders/post/target_" + effect.fileName + ".json");
		PostChain postChain;
		try
		{
			postChain = new PostChain(client.getTextureManager(),
				client.getResourceManager(), renderTarget, shader);
		}catch(IOException | RuntimeException e)
		{
			renderTarget.destroyBuffers();
			throw e;
		}
		target = new EffectTarget(renderTarget, postChain, width, height);
		targets.put(effect, target);
		return target;
	}

	private void composite(RenderTarget source, int width, int height)
	{
		RenderSystem.backupProjectionMatrix();
		PoseStack modelView = RenderSystem.getModelViewStack();
		modelView.pushPose();
		try
		{
			RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width,
				height, 0, 1000, 3000), VertexSorting.ORTHOGRAPHIC_Z);
			modelView.setIdentity();
			modelView.translate(0, 0, -2000);
			RenderSystem.applyModelViewMatrix();
			RenderSystem.viewport(0, 0, width, height);
			RenderSystem.disableDepthTest();
			RenderSystem.depthMask(false);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.setShader(GameRenderer::getPositionTexShader);
			RenderSystem.setShaderTexture(0, source.getColorTextureId());
			RenderSystem.setShaderColor(1, 1, 1, 1);

			float u = source.viewWidth / (float)source.width;
			float v = source.viewHeight / (float)source.height;
			BufferBuilder buffer = Tesselator.getInstance().getBuilder();
			buffer.begin(VertexFormat.Mode.QUADS,
				DefaultVertexFormat.POSITION_TEX);
			buffer.vertex(0, height, 0).uv(0, 0).endVertex();
			buffer.vertex(width, height, 0).uv(u, 0).endVertex();
			buffer.vertex(width, 0, 0).uv(u, v).endVertex();
			buffer.vertex(0, 0, 0).uv(0, v).endVertex();
			BufferUploader.drawWithShader(buffer.end());
		}finally
		{
			modelView.popPose();
			RenderSystem.applyModelViewMatrix();
			RenderSystem.restoreProjectionMatrix();
		}
	}

	private boolean isEmpty()
	{
		for(List<RenderTask> effectTasks : tasks.values())
			if(!effectTasks.isEmpty())
				return false;
		return true;
	}

	public int getQueuedTaskCount()
	{
		int count = 0;
		for(List<RenderTask> effectTasks : tasks.values())
			count += effectTasks.size();
		return count;
	}

	@FunctionalInterface
	public interface RenderTask
	{
		void render(PoseStack matrices, float partialTicks);
	}

	public enum Effect
	{
		OUTLINE("outline"),
		PULSE("pulse"),
		GRADIENT("gradient"),
		SMOKE("smoke");

		private final String fileName;

		Effect(String fileName)
		{
			this.fileName = fileName;
		}

		@Override
		public String toString()
		{
			return Character.toUpperCase(fileName.charAt(0))
				+ fileName.substring(1);
		}
	}

	private static final class EffectTarget
	{
		private final TextureTarget renderTarget;
		private final PostChain postChain;
		private int width;
		private int height;

		private EffectTarget(TextureTarget renderTarget, PostChain postChain,
			int width, int height)
		{
			this.renderTarget = renderTarget;
			this.postChain = postChain;
			this.width = width;
			this.height = height;
		}

		private void resize(int width, int height)
		{
			if(this.width == width && this.height == height)
				return;
			this.width = width;
			this.height = height;
			postChain.resize(width, height);
		}
	}
}
