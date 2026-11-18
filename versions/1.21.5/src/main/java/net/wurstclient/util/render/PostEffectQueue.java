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
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.ProjectionType;

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
			net.wurstclient.util.render.V152Gpu.bind(mainTarget, false);
		}
	}

	private void renderEffect(Minecraft client, RenderTarget mainTarget,
		Effect effect, List<RenderTask> effectTasks, PoseStack matrices,
		float partialTicks) throws IOException
	{
		EffectTarget target = getOrCreateTarget(client, effect,
			mainTarget.width, mainTarget.height);
		target.resize(mainTarget.width, mainTarget.height);
		net.wurstclient.util.render.V152Gpu.clear(target.renderTarget);
		target.renderTarget.copyDepthFrom(mainTarget);
		net.wurstclient.util.render.V152Gpu.bind(target.renderTarget, false);

		for(RenderTask task : effectTasks)
			try(RenderScope ignored = RenderScope.capture())
			{
				task.render(matrices, partialTicks);
			}
        target.postChain.process(target.renderTarget, com.mojang.blaze3d.resource.GraphicsResourceAllocator.UNPOOLED, pass -> pass.setUniform("Time", (System.nanoTime() % 20_000_000_000L) / 20_000_000_000F));
		net.wurstclient.util.render.V152Gpu.bind(mainTarget, false);
		composite(target.renderTarget, mainTarget.width, mainTarget.height);
	}

	private EffectTarget getOrCreateTarget(Minecraft client, Effect effect,
		int width, int height) throws IOException
	{
		EffectTarget target = targets.get(effect);
		if(target != null) {
            target.postChain = client.getShaderManager().getPostChain(
                ResourceLocation.fromNamespaceAndPath(NAMESPACE, "target_" + effect.fileName),
                java.util.Set.of(ResourceLocation.withDefaultNamespace("main")));
            if(target.postChain == null) throw new IOException("Missing target effect: " + effect);
            return target;
        }

		TextureTarget renderTarget = new TextureTarget("WurstB v1.5.2", width, height, true);
		ResourceLocation shader = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "target_" + effect.fileName);
		PostChain postChain;
		try
		{
			postChain = client.getShaderManager().getPostChain(shader, java.util.Set.of(ResourceLocation.withDefaultNamespace("main")));
            if(postChain == null) throw new IOException("Missing target effect: " + shader);
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
		var modelView = RenderSystem.getModelViewStack();
		modelView.pushMatrix();
		try
		{
			RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, width,
				height, 0, 1000, 3000), ProjectionType.ORTHOGRAPHIC);
			modelView.identity();
			modelView.translate(0, 0, -2000);

			com.mojang.blaze3d.opengl.GlStateManager._viewport(0, 0, width, height);
			com.mojang.blaze3d.opengl.GlStateManager._disableDepthTest();
			com.mojang.blaze3d.opengl.GlStateManager._depthMask(false);
			com.mojang.blaze3d.opengl.GlStateManager._enableBlend();
			com.mojang.blaze3d.opengl.GlStateManager._blendFuncSeparate(770, 771, 1, 771);
			net.wurstclient.util.render.V152Gpu.bindTexture(0, net.wurstclient.util.render.V152Gpu.texture(source));
			RenderSystem.setShaderColor(1, 1, 1, 1);

			float u = source.viewWidth / (float)source.width;
			float v = source.viewHeight / (float)source.height;
			BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,
				DefaultVertexFormat.POSITION_TEX);
			buffer.addVertex(0, height, 0).setUv(0, 0);
			buffer.addVertex(width, height, 0).setUv(u, 0);
			buffer.addVertex(width, 0, 0).setUv(u, v);
			buffer.addVertex(0, 0, 0).setUv(0, v);
			net.wurstclient.util.render.V152Gpu.draw(buffer.buildOrThrow());
		}finally
		{
			modelView.popMatrix();

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
		private PostChain postChain;
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
			renderTarget.resize(width, height);
		}
	}
}
