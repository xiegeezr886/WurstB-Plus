package net.wurstclient.hud2.nanovg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

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
	private static final Deque<Slot> available = new ArrayDeque<>();
	private static final Map<GuiRenderState, List<Slot>> pending =
		new IdentityHashMap<>();
	private static int nextTextureId;

	private NanoVgBlitter() {}

	public static synchronized void queueBlit(GuiGraphicsExtractor graphics,
		NanoVGContext ctx)
	{
		if(graphics == null || ctx == null)
			return;

		GuiRenderState state = graphics.getRenderState();
		if(state == null)
			return;

		// The state is reused across frames; reset() releases its snapshots.
		Slot slot = copySnapshot(ctx);
		if(slot == null)
			return;
		pending.computeIfAbsent(state, key -> new ArrayList<>()).add(slot);

		int guiWidth = graphics.guiWidth();
		int guiHeight = graphics.guiHeight();
		state.submitBlitToCurrentLayer(new BlitRenderState(
			RenderPipelines.GUI_TEXTURED,
			TextureSetup.singleTexture(slot.view),
			new Matrix3x2f(), 0, 0, guiWidth, guiHeight, 0F, 1F, 1F, 0F,
			0xFFFFFFFF, null));
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
					TextureFormat.RGBA8, w, h, 1, 1);
				slot.view =
					RenderSystem.getDevice().createTextureView(slot.texture);
				slot.texture.setAddressMode(AddressMode.CLAMP_TO_EDGE);
				slot.texture.setTextureFilter(FilterMode.LINEAR, false);
				slot.width = w;
				slot.height = h;
			}

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
}
