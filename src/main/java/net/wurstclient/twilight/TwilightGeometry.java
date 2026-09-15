/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

/**
 * The pure geometry helpers of the port.
 *
 * <p>
 * Kept free of Skia and Minecraft types on purpose: {@link TwilightSkia} cannot
 * even be initialised without the native Skia library, so anything that can be
 * plain arithmetic lives here where it can be unit tested.
 */
public final class TwilightGeometry
{
	private TwilightGeometry()
	{
		
	}
	
	/**
	 * Fills {@code spans} with the {@code {left, right}} pair of every row of a
	 * triangle, used to draw glyphs without Skia's {@code Path} (which needs the
	 * Kotlin runtime marker {@code KMappedMarker}).
	 *
	 * @param spans
	 *            must hold {@code 2 * rows} floats; rows that do not fit are
	 *            dropped instead of overrunning the array.
	 * @return the number of rows written.
	 */
	public static int triangleSpans(float x1, float y1, float x2, float y2,
		float x3, float y3, float[] spans)
	{
		if(spans == null || spans.length < 2)
			return 0;
		
		int top = (int)Math.floor(Math.min(y1, Math.min(y2, y3)));
		int bottom = (int)Math.ceil(Math.max(y1, Math.max(y2, y3)));
		int rows = Math.max(0, bottom - top);
		
		if(rows == 0)
			return 0;
		
		float[] xs = {x1, x2, x3};
		float[] ys = {y1, y2, y3};
		int written = 0;
		
		for(int row = 0; row < rows && (row + 1) * 2 <= spans.length; row++)
		{
			float y = top + row + 0.5F;
			float left = Float.MAX_VALUE;
			float right = -Float.MAX_VALUE;
			
			for(int edge = 0; edge < 3; edge++)
			{
				int next = (edge + 1) % 3;
				float ay = ys[edge];
				float by = ys[next];
				
				// horizontal edges do not constrain a row
				if(ay == by)
					continue;
				
				float minY = Math.min(ay, by);
				float maxY = Math.max(ay, by);
				
				if(y < minY || y > maxY)
					continue;
				
				float t = (y - ay) / (by - ay);
				float x = xs[edge] + (xs[next] - xs[edge]) * t;
				left = Math.min(left, x);
				right = Math.max(right, x);
			}
			
			if(left > right)
				continue;
			
			spans[written * 2] = left;
			spans[written * 2 + 1] = right;
			written++;
		}
		
		return written;
	}
	
	/** The first row index a triangle touches, the counterpart of the spans. */
	public static int triangleTop(float y1, float y2, float y3)
	{
		return (int)Math.floor(Math.min(y1, Math.min(y2, y3)));
	}
	
	/** The number of rows a triangle covers. */
	public static int triangleRows(float y1, float y2, float y3)
	{
		int top = (int)Math.floor(Math.min(y1, Math.min(y2, y3)));
		int bottom = (int)Math.ceil(Math.max(y1, Math.max(y2, y3)));
		return Math.max(0, bottom - top);
	}
}
