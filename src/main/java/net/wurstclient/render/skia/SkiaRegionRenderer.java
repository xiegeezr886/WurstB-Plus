package net.wurstclient.render.skia;

import java.nio.ByteBuffer;

import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.ColorAlphaType;
import org.jetbrains.skia.ColorInfo;
import org.jetbrains.skia.ColorType;
import org.jetbrains.skia.ImageInfo;
import org.jetbrains.skia.Pixmap;
import org.jetbrains.skia.Surface;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Skia 区域渲染管线（PVPUtils {@code SkiaRenderer} region 路径的 1:1 移植）：
 * 按 GUI 区域创建 CPU raster Surface（分辨率 = 区域尺寸 × guiScale），画布
 * 预置 scale(guiScale) + translate(-region) 让调用方直接用 GUI 坐标绘制，
 * 提交时 peekPixels → glTexSubImage2D 上传 DynamicTexture → GuiGraphics
 * blit 回原区域。GL 直绘后端（{@link SkiaGlBackend}）与 PVPUtils 一样
 * 默认不启用，避免污染 Minecraft 的 GL 状态。
 */
public final class SkiaRegionRenderer
{
	private static final ResourceLocation REGION_TEXTURE_ID =
		new ResourceLocation("wurst", "skia_region");
	private static final long REGION_IDLE_TIMEOUT_MS = 5_000L;

	private static SkiaRegionRenderer instance;

	public static SkiaRegionRenderer get()
	{
		if(instance == null)
			instance = new SkiaRegionRenderer();
		return instance;
	}

	private Surface regionSurface;
	private DynamicTexture regionTexture;
	private int regionPixelW = -1;
	private int regionPixelH = -1;
	private int regionCapacityPixelW = -1;
	private int regionCapacityPixelH = -1;
	private float currentScale = 1;
	private int regionX;
	private int regionY;
	private int regionW;
	private int regionH;
	private boolean regionDrawing;
	private long lastRegionUseMs;

	private SkiaRegionRenderer()
	{}

	/**
	 * 开始区域绘制。返回预置 GUI 坐标变换的画布；native 初始化失败时抛
	 * {@link IllegalStateException}，调用方应回退到 MC 字体渲染路径。
	 */
	public Canvas beginRegion(int x, int y, int w, int h)
	{
		if(regionDrawing)
			return regionSurface != null ? regionSurface.getCanvas() : null;
		if(!SkikoNatives.ensure())
			return null;
		pruneIdle();

		double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
		currentScale = (float)guiScale;
		regionX = x;
		regionY = y;
		regionW = Math.max(1, w);
		regionH = Math.max(1, h);
		regionPixelW = Math.max(1, (int)Math.ceil(regionW * currentScale));
		regionPixelH = Math.max(1, (int)Math.ceil(regionH * currentScale));

		if(regionSurface == null || regionTexture == null
			|| regionPixelW > regionCapacityPixelW
			|| regionPixelH > regionCapacityPixelH)
		{
			int newPixelW = Math.max(regionPixelW, regionCapacityPixelW);
			int newPixelH = Math.max(regionPixelH, regionCapacityPixelH);
			destroyRegionSurface();
			regionSurface = Surface.Companion.makeRaster(
				new ImageInfo(new ColorInfo(ColorType.RGBA_8888,
					ColorAlphaType.UNPREMUL, null), newPixelW, newPixelH));
			regionTexture = new DynamicTexture(newPixelW, newPixelH, false);
			Minecraft.getInstance().getTextureManager()
				.register(REGION_TEXTURE_ID, regionTexture);
			regionCapacityPixelW = newPixelW;
			regionCapacityPixelH = newPixelH;
		}

		Canvas canvas = regionSurface.getCanvas();
		canvas.restoreToCount(1);
		canvas.resetMatrix();
		canvas.clear(0x00000000);
		canvas.save();
		canvas.scale(currentScale, currentScale);
		canvas.translate(-regionX, -regionY);
		regionDrawing = true;
		lastRegionUseMs = System.currentTimeMillis();
		return canvas;
	}

	/** 结束区域绘制：上传像素并 blit 回 GUI。 */
	public void endRegion(GuiGraphics graphics)
	{
		if(!regionDrawing || regionSurface == null || regionTexture == null)
			return;
		regionDrawing = false;
		try
		{
			regionSurface.getCanvas().restore();
			if(uploadRegion())
				graphics.blit(REGION_TEXTURE_ID, regionX, regionY, regionW,
					regionH, 0F, 0F, regionPixelW, regionPixelH,
					regionCapacityPixelW, regionCapacityPixelH);
			lastRegionUseMs = System.currentTimeMillis();
		}finally
		{
			regionDrawing = false;
		}
	}

	private boolean uploadRegion()
	{
		Pixmap pixmap = new Pixmap();
		try
		{
			if(!regionSurface.peekPixels(pixmap))
				return false;
			long addr = pixmap.getAddr();
			int rowBytes = pixmap.getRowBytes();
			ByteBuffer buf = MemoryUtil.memByteBuffer(addr,
				rowBytes * regionCapacityPixelH);
			// 先绑定一次以触发纹理存储分配，再按行距上传 Skia 像素
			regionTexture.bind();
			RenderSystem.pixelStore(3314, rowBytes / 4); // GL_UNPACK_ROW_LENGTH
			GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
				regionCapacityPixelW, regionCapacityPixelH, GL11.GL_RGBA,
				GL11.GL_UNSIGNED_BYTE, buf);
			RenderSystem.pixelStore(3314, 0);
			return true;
		}finally
		{
			pixmap.close();
		}
	}

	private void pruneIdle()
	{
		long now = System.currentTimeMillis();
		if(!regionDrawing && regionSurface != null && lastRegionUseMs > 0
			&& now - lastRegionUseMs > REGION_IDLE_TIMEOUT_MS)
			destroyRegionSurface();
	}

	private void destroyRegionSurface()
	{
		if(regionSurface != null)
		{
			regionSurface.close();
			regionSurface = null;
		}
		if(regionTexture != null)
		{
			Minecraft.getInstance().getTextureManager()
				.release(REGION_TEXTURE_ID);
			regionTexture = null;
		}
	}

	/** 释放全部资源（窗口关闭/GL 上下文重建时调用）。 */
	public void destroy()
	{
		destroyRegionSurface();
		regionPixelW = -1;
		regionPixelH = -1;
		regionCapacityPixelW = -1;
		regionCapacityPixelH = -1;
		regionDrawing = false;
		lastRegionUseMs = 0;
	}
}
