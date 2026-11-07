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
	private static final int MAX_SLOTS = 8;

	private static final java.util.List<Slot> slots = new java.util.ArrayList<>();
	private static GuiRenderState currentState;
	private static int nextSlot;

	private NanoVgBlitter() {}

	public static synchronized void queueBlit(GuiGraphicsExtractor graphics,
		NanoVGContext ctx)
	{
		if(graphics == null || ctx == null)
			return;

		GuiRenderState state = graphics.getRenderState();
		if(state == null)
			return;

		// A new GuiRenderState means a new frame. Passes inside one frame must
		// not share a texture: a later copy would overwrite the image that an
		// earlier queued blit still references.
		if(state != currentState)
		{
			currentState = state;
			nextSlot = 0;
		}

		Slot slot = ensureSlot(nextSlot, ctx);
		if(slot == null)
			return;
		nextSlot = (nextSlot + 1) % MAX_SLOTS;

		int guiWidth = graphics.guiWidth();
		int guiHeight = graphics.guiHeight();
		state.submitBlitToCurrentLayer(new BlitRenderState(
			RenderPipelines.GUI_TEXTURED,
			TextureSetup.singleTexture(slot.view),
			new Matrix3x2f(), 0, 0, guiWidth, guiHeight, 0F, 1F, 1F, 0F,
			0xFFFFFFFF, null));
	}

	private static Slot ensureSlot(int index, NanoVGContext ctx)
	{
		while(slots.size() <= index)
			slots.add(new Slot());

		int w = ctx.getFramebufferWidth();
		int h = ctx.getFramebufferHeight();
		int fbo = ctx.getFramebufferFbo();
		if(w <= 0 || h <= 0 || fbo == 0)
			return null;

		Slot slot = slots.get(index);
		try
		{
			if(slot.texture == null || slot.width != w || slot.height != h)
			{
				slot.close();
				slot.texture = RenderSystem.getDevice().createTexture(
					"wurst-nanovg-" + index,
					GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
					TextureFormat.RGBA8, w, h, 1, 1);
				slot.view =
					RenderSystem.getDevice().createTextureView(slot.texture);
				slot.texture.setAddressMode(AddressMode.CLAMP_TO_EDGE);
				slot.texture.setTextureFilter(FilterMode.LINEAR, false);
				slot.width = w;
				slot.height = h;
			}

			GlTexture glTexture = (GlTexture)slot.texture;
			try(GlStateGuard ignored = GlStateGuard.capture())
			{
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTexture.glId());
				GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fbo);
				GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
				GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0,
					w, h);
			}
			return slot;
		}catch(Throwable t)
		{
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
}
