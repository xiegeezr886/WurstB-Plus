/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.wurstclient.clickgui2.music.NeteaseImageCache;

/**
 * Cover textures with the rounded corners the reference has.
 *
 * <p>
 * Downloads, decoding and the accent colour stay with
 * {@link NeteaseImageCache}, which the other music screens also use - this only
 * adds masked copies. The rounding has to live in the texture rather than in a
 * clip: covers are drawn with vanilla {@code blit} after the Skia region is
 * uploaded, so {@code clipRoundRect} never sees them, the scissor is
 * rectangular, and filling the corners with a background colour would be wrong
 * on glass and gradients.
 *
 * <p>
 * The radius is scaled from the drawn size to the source resolution, so a cover
 * drawn at 44 pixels and the same cover drawn at 300 pixels get the same visual
 * rounding from different textures. Copies are keyed by that source radius, so
 * all the small covers in a list share one.
 */
public final class TwilightCoverCache implements AutoCloseable
{
	/**
	 * Masks are built on the render thread on first use. Building at most one
	 * per few milliseconds spreads a screenful of new covers over several
	 * frames instead of stalling one; until a copy exists the unmasked texture
	 * is drawn, so nothing flickers to empty.
	 */
	private static final long BUILD_INTERVAL_NANOS = 8_000_000L;
	
	private static final int MAX_MASKED = 128;
	
	private final NeteaseImageCache source = new NeteaseImageCache();
	private final Map<String, Texture> masked = new ConcurrentHashMap<>();
	
	private volatile boolean closed;
	private long lastBuildNanos;
	
	/** The unmasked texture, for callers that only want the accent colour. */
	public Texture get(String url)
	{
		return unmasked(baseOf(url));
	}
	
	/**
	 * The cover for {@code url} with {@code radius} pixels of rounding once
	 * drawn into a {@code drawSize} pixel square. Falls back to the unmasked
	 * texture while the mask is being built, and to {@code null} while the
	 * download is still running.
	 */
	public Texture get(String url, int drawSize, int radius)
	{
		NeteaseImageCache.Texture base = baseOf(url);
		
		if(base == null || drawSize <= 0 || radius <= 0)
			return unmasked(base);
		
		int side = Math.min(base.width(), base.height());
		
		if(side <= 0)
			return unmasked(base);
		
		int sourceRadius = TwilightCornerMask
			.clampRadius((int)Math.round(radius * (double)side / drawSize), side);
		
		if(sourceRadius <= 0)
			return unmasked(base);
		
		String key = url + '|' + sourceRadius + '|' + side;
		Texture cached = masked.get(key);
		
		if(cached != null)
			return cached;
		
		long now = System.nanoTime();
		
		if(now - lastBuildNanos < BUILD_INTERVAL_NANOS)
			return unmasked(base);
		
		lastBuildNanos = now;
		Texture built = build(base, side, sourceRadius, key);
		return built != null ? built : unmasked(base);
	}
	
	private NeteaseImageCache.Texture baseOf(String url)
	{
		if(closed || url == null || url.isBlank())
			return null;
		
		return source.get(url);
	}
	
	/** The source cache's record is its own type, so it is wrapped here. */
	private static Texture unmasked(NeteaseImageCache.Texture base)
	{
		return base == null ? null
			: new Texture(base.location(), base.width(), base.height(),
				base.accent());
	}
	
	private Texture build(NeteaseImageCache.Texture base, int side,
		int sourceRadius, String key)
	{
		NativeImage original = pixelsOf(base.location());
		
		if(original == null || original.getWidth() != base.width()
			|| original.getHeight() != base.height())
			return null;
		
		int width = base.width();
		int height = base.height();
		int offsetX = (width - side) / 2;
		int offsetY = (height - side) / 2;
		
		NativeImage copy =
			new NativeImage(NativeImage.Format.RGBA, width, height, false);
		
		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++)
			{
				int coverage = TwilightCornerMask.coverage(x - offsetX,
					y - offsetY, side, sourceRadius);
				copy.setPixelRGBA(x, y, TwilightCornerMask
					.applyAlpha(original.getPixelRGBA(x, y), coverage));
			}
		
		DynamicTexture texture = new DynamicTexture(copy);
		texture.setFilter(true, false);
		ResourceLocation location = new ResourceLocation("wurst",
			"twilight/cover_" + Integer.toUnsignedString(key.hashCode(), 16));
		Minecraft.getInstance().getTextureManager().register(location, texture);
		
		Texture result =
			new Texture(location, width, height, base.accent());
		masked.put(key, result);
		trim();
		return result;
	}
	
	/**
	 * {@code TextureManager.getTexture} hands back the missing texture for
	 * unknown locations, so the cast is the check - no exception to catch.
	 */
	private static NativeImage pixelsOf(ResourceLocation location)
	{
		try
		{
			AbstractTexture texture =
				Minecraft.getInstance().getTextureManager().getTexture(location);
			return texture instanceof DynamicTexture dynamic
				? dynamic.getPixels() : null;
		}catch(RuntimeException e)
		{
			return null;
		}
	}
	
	/**
	 * Keeps the copy count bounded. Dropping a texture that is still being
	 * drawn this frame would be visible, so old copies are only released once
	 * the map is far past what a screenful needs.
	 */
	private void trim()
	{
		if(masked.size() <= MAX_MASKED)
			return;
		
		List<String> keys = new ArrayList<>(masked.keySet());
		Minecraft client = Minecraft.getInstance();
		
		for(int i = 0; i < keys.size() - MAX_MASKED / 2; i++)
		{
			Texture texture = masked.remove(keys.get(i));
			
			if(texture != null)
				client.getTextureManager().release(texture.location());
		}
	}
	
	@Override
	public void close()
	{
		closed = true;
		Minecraft client = Minecraft.getInstance();
		
		for(Texture texture : masked.values())
			client.getTextureManager().release(texture.location());
		
		masked.clear();
		source.close();
	}
	
	public record Texture(ResourceLocation location, int width, int height,
		int accent)
	{}
}
