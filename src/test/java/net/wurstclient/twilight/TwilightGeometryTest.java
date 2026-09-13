/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the scanline triangle that draws the transport glyphs.
 *
 * <p>
 * The glyphs cannot use Skia's {@code Path} (it needs the Kotlin runtime marker
 * {@code KMappedMarker}, which is not on the compile class path), so the
 * triangle is filled with horizontal spans. That part is pure arithmetic and is
 * tested here without a Skia surface.
 */
final class TwilightGeometryTest
{
	@Test
	void aRightTriangleGetsWiderTowardsTheTop()
	{
		// the play glyph shape: a vertical left edge, the tip on the right
		float[] spans = new float[256];
		int rows = TwilightGeometry.triangleSpans(0, 0, 10, 0, 0, 10, spans);
		
		assertEquals(10, rows);
		
		float firstLeft = spans[0];
		float firstRight = spans[1];
		float lastLeft = spans[(rows - 1) * 2];
		float lastRight = spans[(rows - 1) * 2 + 1];
		
		assertEquals(0F, firstLeft, 0.01F);
		assertEquals(0F, lastLeft, 0.01F);
		assertTrue(firstRight > lastRight,
			"the top row must be the wider one: " + firstRight + " vs "
				+ lastRight);
		assertTrue(lastRight >= 0F);
	}
	
	@Test
	void anUprightTriangleIsWidestInTheMiddle()
	{
		// the reference play icon is symmetric around its vertical centre
		float[] spans = new float[256];
		int rows = TwilightGeometry.triangleSpans(-5, -5, 5, 0, -5, 5, spans);
		
		assertEquals(10, rows);
		
		float topWidth = spans[1] - spans[0];
		float middleWidth = spans[5 * 2 + 1] - spans[5 * 2];
		float bottomWidth = spans[(rows - 1) * 2 + 1] - spans[(rows - 1) * 2];
		
		assertTrue(middleWidth > topWidth,
			"middle " + middleWidth + " vs top " + topWidth);
		assertTrue(middleWidth > bottomWidth,
			"middle " + middleWidth + " vs bottom " + bottomWidth);
	}
	
	@Test
	void aFlatTriangleHasNoRows()
	{
		float[] spans = new float[64];
		
		// a perfectly flat triangle covers no rows at all
		assertEquals(0,
			TwilightGeometry.triangleSpans(0, 5, 10, 5, 20, 5, spans));
		
		// a slightly slanted one covers exactly the rows it spans
		assertEquals(2,
			TwilightGeometry.triangleSpans(0, 5, 10, 4, 20, 6, spans));
		assertEquals(2,
			TwilightGeometry.triangleRows(5, 4, 6));
	}
	
	@Test
	void aVerticalTriangleWritesEmptySpans()
	{
		// zero width: the renderer skips these rows instead of drawing them
		float[] spans = new float[256];
		int rows = TwilightGeometry.triangleSpans(5, 0, 5, 10, 5, 0, spans);
		
		assertEquals(10, rows);
		
		for(int row = 0; row < rows; row++)
			assertEquals(spans[row * 2], spans[row * 2 + 1], 0.01F);
	}
	
	@Test
	void aSmallBufferIsNeverOverrun()
	{
		float[] spans = new float[4];
		int rows = TwilightGeometry.triangleSpans(0, 0, 10, 0, 0, 10, spans);
		
		assertTrue(rows <= 2, "wrote " + rows + " rows");
	}
	
	@Test
	void theSpansStayInsideTheTriangle()
	{
		float[] spans = new float[512];
		int rows = TwilightGeometry.triangleSpans(2, 1, 18, 7, 4, 15, spans);
		
		assertTrue(rows > 0);
		
		for(int row = 0; row < rows; row++)
		{
			float left = spans[row * 2];
			float right = spans[row * 2 + 1];
			
			assertTrue(left >= 2F - 0.01F && right <= 18F + 0.01F,
				"row " + row + " was " + left + ".." + right);
		}
	}
}
