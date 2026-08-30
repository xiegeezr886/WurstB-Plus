package net.wurstclient.render.skia;

import org.jetbrains.skia.BackendRenderTarget;
import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.ColorSpace;
import org.jetbrains.skia.DirectContext;
import org.jetbrains.skia.FramebufferFormat;
import org.jetbrains.skia.Surface;
import org.jetbrains.skia.SurfaceColorFormat;
import org.jetbrains.skia.SurfaceOrigin;

/**
 * PVPUtils {@code SkiaGlBackend} 的等价移植：Skia DirectContext 绑定
 * Minecraft 主 framebuffer，提供离屏矢量画布。
 *
 * <p>使用方式：{@link #begin} 获取 Canvas 并绘制，{@link #end} 提交。
 * 窗口尺寸变化或 GL 上下文重建时调用 {@link #destroy} 释放资源。</p>
 */
public final class SkiaGlBackend
{
	private DirectContext context;
	private BackendRenderTarget renderTarget;
	private Surface surface;
	private int lastWidth = -1;
	private int lastHeight = -1;
	private int lastFbo = -1;

	/**
	 * 开始一帧。返回可绘制的 Skia Canvas，失败返回 null。
	 *
	 * @param fboId Minecraft 主 framebuffer 的 FBO id
	 */
	public Canvas begin(int width, int height, int fboId)
	{
		if(width <= 0 || height <= 0)
			return null;
		if(context == null)
		{
			context = DirectContext.Companion.makeGL();
			if(context == null)
				return null;
		}
		if(surface == null || width != lastWidth || height != lastHeight
			|| fboId != lastFbo)
		{
			destroySurface();
			lastWidth = width;
			lastHeight = height;
			lastFbo = fboId;
			renderTarget = BackendRenderTarget.Companion.makeGL(width,
				height, 0, 8, fboId, FramebufferFormat.GR_GL_RGBA8);
			surface = Surface.Companion.makeFromBackendRenderTarget(context,
				renderTarget, SurfaceOrigin.BOTTOM_LEFT,
				SurfaceColorFormat.RGBA_8888,
				ColorSpace.Companion.getSRGB(), null);
		}
		if(surface == null)
			return null;
		context.resetAll();
		return surface.getCanvas();
	}

	/** 提交一帧绘制。 */
	public void end()
	{
		if(surface != null)
			surface.flush();
	}

	private void destroySurface()
	{
		if(surface != null)
		{
			surface.close();
			surface = null;
		}
		if(renderTarget != null)
		{
			renderTarget.close();
			renderTarget = null;
		}
	}

	/** 释放全部资源。 */
	public void destroy()
	{
		destroySurface();
		if(context != null)
		{
			context.close();
			context = null;
		}
		lastWidth = -1;
		lastHeight = -1;
		lastFbo = -1;
	}
}
