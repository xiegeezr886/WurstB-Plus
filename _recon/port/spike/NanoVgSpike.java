package net.wurstclient.hud2.nanovg;

import java.nio.ByteBuffer;
import java.util.OptionalDouble;

import org.joml.Matrix3x2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.opengl.GlTexture;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

/**
 * Feasibility probe for the 26.x render pipeline: draw one frame with NanoVG,
 * copy the offscreen colour texture into a RenderPearl texture, and submit a
 * vanilla GUI blit into the current render state. This is the same path the
 * full HUD will use on the RenderPearl backend.
 */
public final class NanoVgSpike
{
	private static boolean done;
	private static boolean ready;

	private static GpuTexture texture;
	private static GpuTextureView textureView;
	private static GpuSampler sampler;
	private static int textureWidth;
	private static int textureHeight;
	private static java.lang.reflect.Field guiRenderStateField;

	static
	{
		try
		{
			guiRenderStateField = GuiGraphicsExtractor.class
				.getDeclaredField("guiRenderState");
			guiRenderStateField.setAccessible(true);
		}catch(ReflectiveOperationException e)
		{
			System.out.println("[HUD] NanoVG spike: no guiRenderState field: "
				+ e);
		}
	}

	private NanoVgSpike() {}

	public static synchronized void run()
	{
		if(done)
			return;
		done = true;

		NanoVGContext ctx = NanoVGContext.get();
		if(!ctx.create())
		{
			System.out.println("[HUD] NanoVG spike create failed");
			return;
		}

		try(GlStateGuard ignored = GlStateGuard.capture())
		{
			if(!ctx.beginFrame())
			{
				System.out.println("[HUD] NanoVG spike beginFrame failed");
				return;
			}

			ctx.drawRect(10, 10, 240, 90, 0xFF20FF20);
			ctx.drawText("NanoVG 26.3 OK", 24, 40, 0xFFFFFFFF, 16F,
				HudFonts.REGULAR);
			ctx.endFrame();

			int w = ctx.getFramebufferWidth();
			int h = ctx.getFramebufferHeight();
			int fbo = ctx.getFramebufferFbo();
			if(fbo == 0)
			{
				System.out.println("[HUD] NanoVG spike has no framebuffer");
				return;
			}

			GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
			GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
			ByteBuffer pixels = MemoryUtil.memAlloc(w * h * 4);
			try
			{
				GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA,
					GL11.GL_UNSIGNED_BYTE, pixels);
				long nonEmpty = 0;
				int sample = 0;
				int sampleX = w / 2;
				int sampleY = h / 2;
				for(int y = 0; y < h; y++)
					for(int x = 0; x < w; x++)
					{
						int i = (y * w + x) * 4;
						int alpha = pixels.get(i + 3) & 0xFF;
						if(alpha != 0)
							nonEmpty++;
						if(x == sampleX && y == sampleY)
							sample = (pixels.get(i) & 0xFF) << 24
								| (pixels.get(i + 1) & 0xFF) << 16
								| (pixels.get(i + 2) & 0xFF) << 8
								| alpha;
					}
				System.out.println("[HUD] NanoVG spike pixels=" + nonEmpty
					+ "/" + (w * h) + " center=0x"
					+ String.format("%08X", sample));
				ready = true;
			}finally
			{
				MemoryUtil.memFree(pixels);
				GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
			}
		}catch(Throwable t)
		{
			System.out.println("[HUD] NanoVG spike failed: " + t);
			t.printStackTrace();
		}
	}

	/**
	 * Queues a vanilla GUI blit of the NanoVG framebuffer for this frame.
	 */
	public static synchronized void queueBlit(GuiGraphicsExtractor graphics)
	{
		if(!ready || graphics == null)
			return;

		NanoVGContext ctx = NanoVGContext.get();
		if(!ensureTexture(ctx))
			return;

		GuiRenderState state = getGuiRenderState(graphics);
		if(state == null)
			return;

		int guiWidth = graphics.guiWidth();
		int guiHeight = graphics.guiHeight();
		BlitRenderState blit = new BlitRenderState(
			RenderPipelines.GUI_TEXTURED,
			TextureSetup.singleTexture(textureView, sampler), new Matrix3x2f(),
			0, 0, guiWidth, guiHeight, 0F, 1F, 0F, 1F, 0xFFFFFFFF, null);
		state.addGuiElement(blit);
	}

	private static GuiRenderState getGuiRenderState(GuiGraphicsExtractor graphics)
	{
		if(guiRenderStateField == null)
			return null;
		try
		{
			return (GuiRenderState)guiRenderStateField.get(graphics);
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
					"wurst-nanovg-spike",
					GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
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
			System.out.println("[HUD] NanoVG spike blit setup failed: " + t);
			t.printStackTrace();
			return false;
		}
	}
}
