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

	/** Skia surfaces here are always RGBA_8888. */
	private static final int BYTES_PER_PIXEL = 4;

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
			createRegionResources(Math.max(regionPixelW, regionCapacityPixelW),
				Math.max(regionPixelH, regionCapacityPixelH));

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

	/**
	 * Uploads the region into the GL texture.
	 *
	 * <p>
	 * Every failure path here returns false rather than calling into the driver
	 * with data it cannot trust: this method is what crashed the NVIDIA OpenGL
	 * driver once already (an access violation inside
	 * {@code glTexSubImage2D}), and a frame that is skipped is always better
	 * than a driver crash.
	 */
	private boolean uploadRegion()
	{
		Pixmap pixmap = new Pixmap();
		boolean packed = false;
		ByteBuffer buffer = null;
		try
		{
			if(!regionSurface.peekPixels(pixmap))
				return false;

			long address = pixmap.getAddr();
			int rowBytes = pixmap.getRowBytes();
			int rowPixels = rowBytes / BYTES_PER_PIXEL;

			if(address == 0 || rowBytes <= 0
				|| rowPixels < regionCapacityPixelW)
				return false;

			int rowLength;

			if(rowBytes % BYTES_PER_PIXEL == 0)
			{
				buffer = MemoryUtil.memByteBuffer(address,
					rowBytes * regionCapacityPixelH);
				rowLength = rowPixels;
			}else
			{
				// GL_UNPACK_ROW_LENGTH counts pixels, so Skia's byte padding
				// cannot be expressed through it. Copy into tight rows instead,
				// which also lets us upload with the row length turned off.
				buffer = packRows(address, rowBytes, regionCapacityPixelW,
					regionCapacityPixelH);
				packed = true;
				rowLength = 0;
			}

			bindRegionTexture();

			// MC tracks pixel store state, so it has to be set through it, and
			// every field that could still hold a stale value is reset here.
			RenderSystem.pixelStore(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
			RenderSystem.pixelStore(GL11.GL_UNPACK_ALIGNMENT,
				BYTES_PER_PIXEL);
			RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
			RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);

			try
			{
				GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0,
					regionCapacityPixelW, regionCapacityPixelH, GL11.GL_RGBA,
					GL11.GL_UNSIGNED_BYTE, buffer);
			}finally
			{
				RenderSystem.pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
			}

			return true;

		}catch(RuntimeException e)
		{
			return false;

		}finally
		{
			if(packed && buffer != null)
				MemoryUtil.memFree(buffer);

			pixmap.close();
		}
	}

	/**
	 * Copies pixels into a contiguous block of {@code width * 4} byte rows,
	 * ignoring whatever padding sits between the source rows.
	 *
	 * <p>
	 * Package private and free of GL and Skia types so the packing can be unit
	 * tested: getting it wrong is what feeds the driver a bad row pitch.
	 *
	 * @return a newly allocated buffer the caller must free.
	 */
	static ByteBuffer packRows(long address, int rowBytes, int width,
		int height)
	{
		int packedRowBytes = width * BYTES_PER_PIXEL;
		ByteBuffer packedBuffer =
			MemoryUtil.memAlloc(packedRowBytes * height);
		ByteBuffer source = MemoryUtil.memByteBuffer(address,
			rowBytes * height);

		for(int row = 0; row < height; row++)
		{
			int start = row * rowBytes;
			source.limit(start + packedRowBytes).position(start);
			packedBuffer.put(source);
		}

		return packedBuffer.flip();
	}

	/**
	 * Binds the region texture, recreating it when the texture manager no longer
	 * holds it - a resource reload releases every registered texture, and
	 * uploading into one that has been released is another way to hand the
	 * driver a dead texture name.
	 */
	private void bindRegionTexture()
	{
		if(Minecraft.getInstance().getTextureManager()
			.getTexture(REGION_TEXTURE_ID) != regionTexture)
			recreateRegionTexture();

		regionTexture.bind();
	}

	/** Creates the raster surface and its matching GL texture. */
	private void createRegionResources(int pixelW, int pixelH)
	{
		destroyRegionSurface();
		regionSurface = Surface.Companion.makeRaster(
			new ImageInfo(new ColorInfo(ColorType.RGBA_8888,
				ColorAlphaType.UNPREMUL, null), pixelW, pixelH));
		regionCapacityPixelW = pixelW;
		regionCapacityPixelH = pixelH;
		recreateRegionTexture();
	}

	/**
	 * Creates only the texture, leaving the surface alone - the surface holds
	 * the frame that is being uploaded and must never be dropped here.
	 */
	private void recreateRegionTexture()
	{
		regionTexture = new DynamicTexture(regionCapacityPixelW,
			regionCapacityPixelH, false);
		Minecraft.getInstance().getTextureManager().register(REGION_TEXTURE_ID,
			regionTexture);
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

	/**
	 * 本帧是否已经有一个区域正在绘制。
	 *
	 * <p>
	 * 管线一帧只支持一个区域，而 {@link #beginRegion} 在已有区域时只会把
	 * <b>同一块画布</b>原样返回、不会重新施加缩放与平移。第二个调用方若直接
	 * 往上画，就会用到上一个调用方的坐标变换而把画面写花。所以后到者应当先问
	 * 这一句，然后回退到自己的原版兜底路径。
	 */
	public boolean isRegionDrawing()
	{
		return regionDrawing;
	}
}
