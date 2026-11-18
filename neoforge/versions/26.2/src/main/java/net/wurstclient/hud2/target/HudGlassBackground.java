package net.wurstclient.hud2.target;

import java.io.IOException;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.nanovg.GlStateGuard;
import net.wurstclient.hud2.nanovg.NanoVGContext;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** Shared low-rate full-screen backdrop used by every standard HUD card. */
public final class HudGlassBackground implements AutoCloseable
{
	private RenderTarget capture;
	private net.wurstclient.util.render.V152BackdropBlur blur;
	private int image;
	private long context;
	private int captureWidth, captureHeight, guiWidth, guiHeight;
	private long refreshedAt;
	private long refreshedFrame = -1;
	private boolean failed;

	public void draw(NanoVGContext nvg, float x, float y, float width,
		float height, float radius, float partialTicks)
	{
		if(failed || width <= 0 || height <= 0 || nvg.isGles()) return;
		var mc = WurstClient.MC;
		if(mc == null || mc.getWindow() == null) return;
		guiWidth = mc.getWindow().getGuiScaledWidth();
		guiHeight = mc.getWindow().getGuiScaledHeight();
		long now = System.nanoTime();
		if(refreshedFrame != nvg.getFrameSerial() && (capture == null || now - refreshedAt >= 33_333_333L
			|| captureWidth != mc.gameRenderer.mainRenderTarget().width
			|| captureHeight != mc.gameRenderer.mainRenderTarget().height))
		{
			refresh(mc, partialTicks, now);
			refreshedFrame = nvg.getFrameSerial();
		}
		if(capture == null) return;
		if(context != nvg.getHandle()) {image = 0; context = nvg.getHandle();}
		if(image == 0)
			image = NanoVGGL3.nvglCreateImageFromHandle(context,
				net.wurstclient.util.render.V152Gpu.texture(capture), capture.width, capture.height,
				NanoVGGL3.NVG_IMAGE_NODELETE | NanoVG.NVG_IMAGE_FLIPY);
		if(image == 0) return;
		try(MemoryStack stack = MemoryStack.stackPush())
		{
			NVGPaint paint = NVGPaint.malloc(stack);
			NanoVG.nvgBeginPath(context);
			NanoVG.nvgRoundedRect(context, x, y, width, height, radius);
			// The path has the card's animation transform. Sample the world in
			// screen coordinates so the glass never slides or scales the scene.
			NanoVG.nvgSave(context);
			try
			{
				NanoVG.nvgResetTransform(context);
				NanoVG.nvgScale(context, nvg.getGuiScale(), nvg.getGuiScale());
				NanoVG.nvgImagePattern(context, 0, 0, guiWidth, guiHeight, 0,
					image, 1, paint);
				NanoVG.nvgFillPaint(context, paint);
				NanoVG.nvgFill(context);
			}finally {NanoVG.nvgRestore(context);}
		}
	}

	private void refresh(net.minecraft.client.Minecraft mc, float partialTicks,
		long now)
	{
		RenderSystem.backupProjectionMatrix();
		var matrix = RenderSystem.getModelViewStack();
		matrix.pushMatrix();
		try(GlStateGuard ignored = GlStateGuard.capture())
		{
			RenderTarget main = mc.gameRenderer.mainRenderTarget();
			float ratio = Math.min(.5F,
				Math.min(640F / main.width, 360F / main.height));
			int width = Math.max(1, Math.round(main.width * ratio));
			int height = Math.max(1, Math.round(main.height * ratio));
			if(capture == null)
			{
				capture = new TextureTarget("WurstB v1.5.2", width, height, false, com.mojang.blaze3d.GpuFormat.RGBA8_UNORM);
				blur = new net.wurstclient.util.render.V152BackdropBlur();
			}
			if(capture.width != width || capture.height != height)
			{
				deleteImage();
				capture.resize(width, height);
			}
			captureWidth = main.width;
			captureHeight = main.height;
			com.mojang.blaze3d.opengl.GlStateManager._disableScissorTest();
			GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, net.wurstclient.util.render.V152Gpu.fbo(main));
			GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, net.wurstclient.util.render.V152Gpu.fbo(capture));
			GL30.glBlitFramebuffer(0, 0, main.width, main.height, 0, 0,
				width, height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
			blur.process(capture);
			refreshedAt = now;
		}catch(IOException | RuntimeException e)
		{
			failed = true;
			try(GlStateGuard ignored = GlStateGuard.capture()) {close();}
			System.err.println("[HUD] Glass backdrop unavailable: " + e);
		}finally
		{
			matrix.popMatrix();
			
			RenderSystem.restoreProjectionMatrix();
		}
	}

	private void deleteImage()
	{
		if(image != 0 && context == NanoVGContext.get().getHandle())
			NanoVG.nvgDeleteImage(context, image);
		image = 0;
	}

	@Override public void close()
	{
		deleteImage();
		if(blur != null) {blur.close(); blur = null;}
		if(capture != null) {capture.destroyBuffers(); capture = null;}
		captureWidth = captureHeight = 0;
		refreshedAt = 0;
		refreshedFrame = -1;
	}
}
