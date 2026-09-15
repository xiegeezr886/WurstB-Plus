/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

/**
 * The cover fit of the reference, i.e. CSS {@code object-fit: cover}.
 *
 * <p>
 * Twilight Echo shows album art in fixed squares of many sizes (218px in the
 * hero, 60px in the duo stack, 44px in the player bar) while the art itself can
 * be any aspect ratio, so every cover is scaled to fill the box and cropped
 * around its centre.
 *
 * <p>
 * Deliberately free of Minecraft types so that the crop can be unit tested.
 */
public final class TwilightCoverFit
{
	private TwilightCoverFit()
	{
		
	}
	
	/**
	 * @return {@code {u, v, width, height}} in texture pixels, i.e. the part of
	 *         the cover that has to be drawn to fill the target box without
	 *         distortion. An empty rectangle is returned for degenerate input.
	 */
	public static int[] sourceRect(int textureWidth, int textureHeight,
		int targetWidth, int targetHeight)
	{
		if(textureWidth <= 0 || textureHeight <= 0 || targetWidth <= 0
			|| targetHeight <= 0)
			return new int[]{0, 0, 0, 0};
		
		// the scale that covers the box, so the source keeps the box aspect
		double targetAspect = targetWidth / (double)targetHeight;
		double textureAspect = textureWidth / (double)textureHeight;
		
		int width;
		int height;
		
		if(textureAspect > targetAspect)
		{
			// the cover is wider than the box: crop the sides
			height = textureHeight;
			width = (int)Math.round(textureHeight * targetAspect);
		}else
		{
			// the cover is taller than the box: crop top and bottom
			width = textureWidth;
			height = (int)Math.round(textureWidth / targetAspect);
		}
		
		width = Math.max(1, Math.min(width, textureWidth));
		height = Math.max(1, Math.min(height, textureHeight));
		
		int u = Math.max(0, (textureWidth - width) / 2);
		int v = Math.max(0, (textureHeight - height) / 2);
		return new int[]{u, v, width, height};
	}
	
	/**
	 * The same as {@link #sourceRect(int, int, int, int)} for a square box, the
	 * only shape the reference uses.
	 */
	public static int[] squareRect(int textureWidth, int textureHeight,
		int size)
	{
		return sourceRect(textureWidth, textureHeight, size, size);
	}
}
