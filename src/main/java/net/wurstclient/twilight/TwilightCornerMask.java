/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

/**
 * Rounded-corner alpha mask for cover art.
 *
 * <p>
 * The reference rounds every cover, and the port could not follow: covers are
 * drawn with vanilla {@code GuiGraphics.blit} after the Skia region has been
 * uploaded, so {@code clipRoundRect} does not apply to them, and the rectangular
 * scissor cannot round anything. Painting the corners over with the background
 * colour is wrong too, because covers sit on glass and gradients rather than a
 * flat colour.
 *
 * <p>
 * So the rounding is baked into the texture instead: the corner pixels get a
 * partial alpha, and whatever is behind the cover shows through. Coverage is
 * supersampled so the edges are anti-aliased like the CSS {@code border-radius}
 * the reference uses.
 *
 * <p>
 * Pure maths on purpose - no Minecraft types - so it can be unit tested.
 */
public final class TwilightCornerMask
{
	/** Samples per axis per pixel. 4 means 16 samples and 17 alpha steps. */
	public static final int SAMPLES = 4;
	
	private TwilightCornerMask()
	{}
	
	/**
	 * Alpha multiplier for one pixel of a {@code size} x {@code size} square
	 * whose corners are rounded with {@code radius}: 255 keeps the pixel, 0
	 * removes it, values in between are the anti-aliased edge.
	 */
	public static int coverage(int x, int y, int size, int radius)
	{
		if(size <= 0)
			return 0;
		
		if(x < 0 || y < 0 || x >= size || y >= size)
			return 0;
		
		int r = clampRadius(radius, size);
		
		if(r <= 0 || !isCorner(x, y, size, r))
			return 255;
		
		int inside = 0;
		int total = SAMPLES * SAMPLES;
		
		for(int sy = 0; sy < SAMPLES; sy++)
			for(int sx = 0; sx < SAMPLES; sx++)
				if(isInside(x + (sx + 0.5D) / SAMPLES,
					y + (sy + 0.5D) / SAMPLES, size, r))
					inside++;
		
		return inside * 255 / total;
	}
	
	/**
	 * Multiplies the alpha byte of a pixel packed either as RGBA or ABGR; the
	 * alpha byte is the top one in both, so the colour channels pass through
	 * untouched without knowing which order the image uses.
	 */
	public static int applyAlpha(int pixel, int coverage)
	{
		if(coverage >= 255)
			return pixel;
		
		if(coverage <= 0)
			return pixel & 0x00FFFFFF;
		
		int alpha = (pixel >>> 24) & 0xFF;
		return (alpha * coverage / 255) << 24 | pixel & 0x00FFFFFF;
	}
	
	/**
	 * A radius larger than half the image would make opposite corners overlap,
	 * which the rounded-box test below cannot express, so it is clamped - the
	 * same thing CSS does for oversized radii.
	 */
	public static int clampRadius(int radius, int size)
	{
		if(size <= 0 || radius <= 0)
			return 0;
		
		return Math.min(radius, size / 2);
	}
	
	/**
	 * Point-in-rounded-box test: the point is inside when its distance to the
	 * inner rectangle (the box shrunk by the radius) is at most the radius.
	 */
	static boolean isInside(double x, double y, int size, int radius)
	{
		double dx = Math.max(Math.max(radius - x, x - (size - radius)), 0);
		double dy = Math.max(Math.max(radius - y, y - (size - radius)), 0);
		return dx * dx + dy * dy <= (double)radius * radius;
	}
	
	/** Only the four radius-sized corner squares can be partially covered. */
	static boolean isCorner(int x, int y, int size, int radius)
	{
		boolean horizontal = x < radius || x >= size - radius;
		boolean vertical = y < radius || y >= size - radius;
		return horizontal && vertical;
	}
}
