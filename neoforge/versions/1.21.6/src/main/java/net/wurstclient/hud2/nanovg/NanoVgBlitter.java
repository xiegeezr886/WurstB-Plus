package net.wurstclient.hud2.nanovg;

import org.joml.Matrix3x2f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.wurstclient.util.render.GuiGraphicsExtractor;

/**
 * Copies NanoVG's offscreen framebuffer into a GPU texture and queues a blit
 * in the current GUI render state. Used by 1.21.6-1.21.10.
 */
public final class NanoVgBlitter
{
	private static GpuTexture texture;
	private static GpuTextureView textureView;
	private static int textureWidth;
	private static int textureHeight;

	private NanoVgBlitter() {}

	public static synchronized void queueBlit(GuiGraphicsExtractor graphics,
		NanoVGContext ctx)
	{
		if(graphics == null || ctx == null || !ensureTexture(ctx))
			return;

		GuiRenderState state = graphics.getRenderState();
		if(state == null)
			return;

		int guiWidth = graphics.guiWidth();
		int guiHeight = graphics.guiHeight();
		state.submitBlitToCurrentLayer(new BlitRenderState(
			RenderPipelines.GUI_TEXTURED,
			TextureSetup.singleTexture(textureView), new Matrix3x2f(), 0, 0,
			guiWidth, guiHeight, 0F, 1F, 1F, 0F, 0xFFFFFFFF, null));
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
					GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
					TextureFormat.RGBA8, w, h, 1, 1);
				textureView =
					RenderSystem.getDevice().createTextureView(texture);
				texture.setAddressMode(AddressMode.CLAMP_TO_EDGE);
				texture.setTextureFilter(FilterMode.LINEAR, false);
				textureWidth = w;
				textureHeight = h;
			}

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
}
