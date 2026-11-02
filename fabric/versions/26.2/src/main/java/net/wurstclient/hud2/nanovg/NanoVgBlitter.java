package net.wurstclient.hud2.nanovg;

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
	private static GpuTexture texture;
	private static GpuTextureView textureView;
	private static GpuSampler sampler;
	private static int textureWidth;
	private static int textureHeight;

	private NanoVgBlitter() {}

	public static synchronized void queueBlit(GuiGraphicsExtractor graphics,
		NanoVGContext ctx)
	{
		if(graphics == null || ctx == null || !ensureTexture(ctx))
			return;

		GuiRenderState state = getState(graphics);
		if(state == null)
			return;

		int guiWidth = graphics.guiWidth();
		int guiHeight = graphics.guiHeight();
		state.addBlitToCurrentLayer(new BlitRenderState(
			RenderPipelines.GUI_TEXTURED,
			TextureSetup.singleTexture(textureView, sampler),
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

	private static boolean ensureTexture(NanoVGContext ctx)
	{
		int w = ctx.getFramebufferWidth();
		int h = ctx.getFramebufferHeight();
		int fbo = ctx.getFramebufferFbo();
		if(w <= 0 || h <= 0 || fbo == 0)
			return false;

		try
		{
			if(texture == null || textureWidth != w || textureHeight != h)
			{
				if(textureView != null)
					textureView.close();
				if(texture != null)
					texture.close();

				texture = RenderSystem.getDevice().createTexture(
					"wurst-nanovg",
					GpuTexture.USAGE_COPY_DST
						| GpuTexture.USAGE_TEXTURE_BINDING,
					GpuFormat.RGBA8_UNORM, w, h, 1, 1);
				textureView =
					RenderSystem.getDevice().createTextureView(texture);
				textureWidth = w;
				textureHeight = h;
			}

			if(sampler == null)
				sampler = RenderSystem.getDevice().createSampler(
					AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
					FilterMode.LINEAR, FilterMode.LINEAR, 1,
					OptionalDouble.empty());

			GlTexture glTexture = (GlTexture)texture;
			try(GlStateGuard ignored = GlStateGuard.capture())
			{
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTexture.glId());
				GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
				GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
				GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
					w, h);
			}
			return true;
		}catch(Throwable t)
		{
			System.out.println("[HUD] NanoVG blit setup failed: " + t);
			t.printStackTrace();
			return false;
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
