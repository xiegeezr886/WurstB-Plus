package net.wurstclient.hud2.nanovg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import java.util.OptionalDouble;

import org.joml.Matrix3x2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.GpuFormat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * Copies NanoVG's offscreen framebuffer into a GPU texture and queues a blit
 * in the current GUI render state. Used by 26.1/26.2.
 */
public final class NanoVgBlitter
{
	private static final Deque<Slot> available = new ArrayDeque<>();
	private static final Map<GuiRenderState, List<Slot>> pending =
		new IdentityHashMap<>();
	private static GpuSampler sampler;
	private static int nextTextureId;

	private NanoVgBlitter() {}

	public static synchronized void queueBlit(GuiGraphicsExtractor graphics,
		NanoVGContext ctx)
	{
		if(graphics == null || ctx == null)
			return;

		GuiRenderState state = getState(graphics);
		if(state == null)
			return;

		// The state is reused across frames; reset() releases its snapshots.
		Slot slot = copySnapshot(ctx);
		if(slot == null)
			return;
		pending.computeIfAbsent(state, key -> new ArrayList<>()).add(slot);

		int guiWidth = graphics.guiWidth();
		int guiHeight = graphics.guiHeight();
		state.addBlitToCurrentLayer(new BlitRenderState(
			RenderPipelines.GUI_TEXTURED,
			TextureSetup.singleTexture(slot.view, sampler),
			new Matrix3x2f(), 0, 0, guiWidth, guiHeight, 0F, 1F, 1F, 0F,
			0xFFFFFFFF, null));
	}

	private static GuiRenderState getState(GuiGraphicsExtractor graphics)
	{
		if(GuiGraphicsExtractorAccessor.Holder.FIELD == null)
			return null;
		try
		{
			return (GuiRenderState)GuiGraphicsExtractorAccessor.Holder.FIELD
				.get(graphics);
		}catch(ReflectiveOperationException e)
		{
			return null;
		}
	}

	public static synchronized void releaseFrame(GuiRenderState state)
	{
		List<Slot> consumed = pending.remove(state);
		if(consumed != null)
			available.addAll(consumed);
	}

	public static synchronized void close()
	{
		for(List<Slot> frame : pending.values())
			for(Slot slot : frame)
				slot.close();
		pending.clear();
		for(Slot slot : available)
			slot.close();
		available.clear();
		if(sampler != null)
		{
			sampler.close();
			sampler = null;
		}
	}

	private static Slot copySnapshot(NanoVGContext ctx)
	{
		int w = ctx.getFramebufferWidth();
		int h = ctx.getFramebufferHeight();
		int fbo = ctx.getFramebufferFbo();
		if(w <= 0 || h <= 0 || fbo == 0)
			return null;

		Slot slot = available.pollFirst();
		if(slot == null)
			slot = new Slot();
		try(GlStateGuard ignored = GlStateGuard.capture())
		{
			if(slot.texture == null || slot.width != w || slot.height != h)
			{
				slot.close();
				slot.texture = RenderSystem.getDevice().createTexture(
					"wurst-nanovg-" + nextTextureId++,
					GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
					GpuFormat.RGBA8_UNORM, w, h, 1, 1);
				slot.view =
					RenderSystem.getDevice().createTextureView(slot.texture);
				slot.width = w;
				slot.height = h;
			}
			if(sampler == null)
				sampler = RenderSystem.getDevice().createSampler(
					AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
					FilterMode.LINEAR, FilterMode.LINEAR, 1,
					OptionalDouble.empty());

			GlTexture glTexture = (GlTexture)slot.texture;
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTexture.glId());
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
			GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
			GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
			return slot;
		}catch(Throwable t)
		{
			slot.close();
			System.out.println("[HUD] NanoVG blit setup failed: " + t);
			t.printStackTrace();
			return null;
		}
	}

	private static final class Slot
	{
		private GpuTexture texture;
		private GpuTextureView view;
		private int width;
		private int height;

		private void close()
		{
			if(view != null)
			{
				view.close();
				view = null;
			}
			if(texture != null)
			{
				texture.close();
				texture = null;
			}
			width = 0;
			height = 0;
		}
	}

	/** Kept separate so the accessor failure is reported once. */
	private static final class GuiGraphicsExtractorAccessor
	{
		private static final class Holder
		{
			private static final java.lang.reflect.Field FIELD = findField();

			private static java.lang.reflect.Field findField()
			{
				try
				{
					java.lang.reflect.Field field =
						GuiGraphicsExtractor.class
							.getDeclaredField("guiRenderState");
					field.setAccessible(true);
					return field;
				}catch(ReflectiveOperationException e)
				{
					System.out.println(
						"[HUD] NanoVG: no guiRenderState field: " + e);
					return null;
				}
			}
		}
	}
}
