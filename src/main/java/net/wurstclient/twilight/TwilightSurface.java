/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

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
import net.wurstclient.WurstClient;
import net.wurstclient.render.skia.SkikoNatives;

/**
 * A Skia surface that is rasterised once and then reused for as long as its
 * content stays valid.
 *
 * <p>
 * This is what makes vector-crisp UI affordable: drawing the interface with Skia
 * gives real rounded corners, real gradients and the bundled PingFang outlines
 * at any size, but rasterising the whole window every frame costs tens of
 * milliseconds and uploads megabytes each time. So the caller paints into
 * {@link #begin(int, int)} only when it returns a canvas - that is, when the
 * content changed - and every other frame just blits the texture that is already
 * on the GPU. One quad per frame, one upload per change.
 *
 * <p>
 * The upload deliberately avoids {@code GL_UNPACK_ROW_LENGTH}: rows are made
 * contiguous first, so the driver is never asked to walk a pitch. That
 * combination is what crashed the NVIDIA driver in the ESP path, and a skipped
 * or copied row is not worth a hard crash.
 */
final class TwilightSurface implements AutoCloseable
{
	/**
	 * Upper bound for the rasterised layer, in pixels. A 4K window at GUI scale
	 * 3 would otherwise ask for a 33 megapixel surface - 132 MB - just to draw a
	 * music player, so beyond this the layer is rasterised at a lower resolution
	 * and stretched back up.
	 */
	private static final int MAX_PIXELS = 8_000_000;

	/** Idle time after which the surface is dropped, so a closed menu stops
	 * holding tens of megabytes. */
	private static final long IDLE_TIMEOUT_MS = 60_000L;

	private final ResourceLocation location;
	private final String debugName;

	private Surface surface;
	private DynamicTexture texture;
	private int pixelW = -1;
	private int pixelH = -1;

	/** The GUI scale the layer is rasterised at, which can be lower than the
	 * window's own scale when {@link #MAX_PIXELS} is hit. */
	private float rasterScale = 1F;

	private boolean dirty = true;
	private boolean failed;
	private long lastUseMs;

	TwilightSurface(String debugName)
	{
		this.debugName = debugName;
		location = new ResourceLocation(WurstClient.MOD_ID,
			"twilight_layer_" + debugName);
	}

	/**
	 * @return the canvas to paint the layer into, or null when the layer is
	 *         still valid and the caller should only {@link #blit} it again
	 */
	Canvas begin(int guiWidth, int guiHeight)
	{
		if(failed || guiWidth <= 0 || guiHeight <= 0)
			return null;

		try
		{
			// ensure() THROWS rather than returning false once it has latched a
			// failure (see SkikoNatives' failure contract). This layer's whole job
			// is to degrade to the vanilla renderer, so it must absorb that throw:
			// on a platform without the bundled natives, or after any transient
			// first-frame failure, an escaping exception would reach
			// TwilightShellScreen.render() and take the client down.
			if(!SkikoNatives.ensure())
			{
				failed = true;
				return null;
			}
		}catch(Throwable t)
		{
			failed = true;
			return null;
		}

		pruneIdle();

		double windowScale = Minecraft.getInstance().getWindow().getGuiScale();
		double budget = Math.sqrt(
			MAX_PIXELS / (double)((long)guiWidth * guiHeight));
		float wanted = (float)Math.min(windowScale, budget);
		int wantedW = Math.max(1, (int)Math.ceil(guiWidth * wanted));
		int wantedH = Math.max(1, (int)Math.ceil(guiHeight * wanted));

		try
		{
			if(surface == null || texture == null || wantedW != pixelW
				|| wantedH != pixelH)
			{
				create(wantedW, wantedH, wanted);
				dirty = true;
			}else if(textureDropped())
			{
				// a resource reload released it; rasterise it again
				recreateTexture();
				dirty = true;
			}
		}catch(Throwable t)
		{
			// the native Skia library is unusable here: the caller falls back to
			// vanilla primitives rather than losing the interface
			failed = true;
			destroySurface();
			return null;
		}

		lastUseMs = System.currentTimeMillis();

		if(!dirty)
			return null;

		Canvas canvas = surface.getCanvas();
		canvas.restoreToCount(1);
		canvas.resetMatrix();
		canvas.clear(0x00000000);
		canvas.save();
		canvas.scale(rasterScale, rasterScale);
		return canvas;
	}

	/** Whether Skia turned out to be unusable, so the caller should fall back. */
	boolean hasFailed()
	{
		return failed;
	}

	/**
	 * Uploads what was painted since {@link #begin} returned a canvas. Must be
	 * called on the render thread.
	 */
	void commit()
	{
		if(surface == null || texture == null)
			return;

		upload();
		dirty = false;
		lastUseMs = System.currentTimeMillis();
	}

	/** Draws the cached layer into the GUI, one texture, one quad. */
	void blit(GuiGraphics graphics, int x, int y, int width, int height)
	{
		if(texture == null || pixelW <= 0 || pixelH <= 0 || width <= 0
			|| height <= 0)
			return;

		graphics.blit(location, x, y, width, height, 0F, 0F, pixelW, pixelH,
			pixelW, pixelH);
	}

	/** Marks the layer stale; the next {@link #begin} repaints it. */
	void invalidate()
	{
		dirty = true;
	}

	boolean isReady()
	{
		return texture != null && !dirty;
	}

	int pixelWidth()
	{
		return pixelW;
	}

	int pixelHeight()
	{
		return pixelH;
	}

	@Override
	public void close()
	{
		destroySurface();
	}

	private void create(int width, int height, float scale)
	{
		destroySurface();
		surface = Surface.Companion.makeRaster(new ImageInfo(new ColorInfo(
			ColorType.RGBA_8888, ColorAlphaType.UNPREMUL, null), width,
			height));
		pixelW = width;
		pixelH = height;
		rasterScale = scale;
		recreateTexture();
	}

	private void recreateTexture()
	{
		texture = new DynamicTexture(pixelW, pixelH, false);
		Minecraft.getInstance().getTextureManager().register(location, texture);
	}

	private boolean textureDropped()
	{
		return Minecraft.getInstance().getTextureManager()
			.getTexture(location) != texture;
	}

	/**
	 * Uploads the surface into its texture.
	 *
	 * <p>
	 * The rows are copied into a contiguous block whenever Skia's row pitch is
	 * not exactly {@code width * 4}, and the row length is left at GL's default
	 * either way: the driver then reads exactly the bytes that were handed to
	 * it.
	 */
	private void upload()
	{
		Pixmap pixmap = new Pixmap();
		ByteBuffer buffer = null;
		boolean packed = false;

		try
		{
			if(!surface.peekPixels(pixmap))
				return;

			long address = pixmap.getAddr();
			int rowBytes = pixmap.getRowBytes();
			int tightRowBytes = pixelW * 4;

			if(address == 0 || rowBytes <= 0 || rowBytes < tightRowBytes)
				return;

			if(rowBytes == tightRowBytes)
				buffer = MemoryUtil.memByteBuffer(address, rowBytes * pixelH);
			else
			{
				buffer = packRows(address, rowBytes, tightRowBytes, pixelH);
				packed = true;
			}

			if(textureDropped())
				recreateTexture();

			texture.bind();
			RenderSystem.pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
			RenderSystem.pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
			RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
			RenderSystem.pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);

			GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, pixelW, pixelH,
				GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

		}catch(RuntimeException e)
		{
			// a skipped upload shows the previous frame, which beats crashing
		}finally
		{
			if(packed && buffer != null)
				MemoryUtil.memFree(buffer);

			pixmap.close();
		}
	}

	/** Copies rows into a contiguous block, ignoring Skia's row padding. */
	static ByteBuffer packRows(long address, int rowBytes, int tightRowBytes,
		int height)
	{
		ByteBuffer packed = MemoryUtil.memAlloc(tightRowBytes * height);
		ByteBuffer source = MemoryUtil.memByteBuffer(address,
			rowBytes * height);

		for(int row = 0; row < height; row++)
		{
			int start = row * rowBytes;
			source.limit(start + tightRowBytes).position(start);
			packed.put(source);
		}

		return packed.flip();
	}

	private void pruneIdle()
	{
		if(surface == null || dirty)
			return;

		if(System.currentTimeMillis() - lastUseMs > IDLE_TIMEOUT_MS)
			destroySurface();
	}

	private void destroySurface()
	{
		if(texture != null)
		{
			Minecraft.getInstance().getTextureManager().release(location);
			texture = null;
		}

		if(surface != null)
		{
			surface.close();
			surface = null;
		}

		pixelW = -1;
		pixelH = -1;
		dirty = true;
	}
}
