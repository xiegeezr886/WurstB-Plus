/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.wurstclient.perimeter.config.PerimeterDetectedArea;
import net.wurstclient.perimeter.config.PerimeterScanline;

final class PerimeterColumnAreaTest
{
	@Test
	void exposesTheInclusiveBounds()
	{
		PerimeterColumnArea area = area(List.of(new PerimeterScanline(10, 5, 8),
			new PerimeterScanline(12, -3, -1)), -59, 79);
		
		assertEquals(-3, area.minX());
		assertEquals(8, area.maxX());
		assertEquals(10, area.minZ());
		assertEquals(12, area.maxZ());
		assertEquals(-59, area.minY());
		assertEquals(79, area.maxY());
	}
	
	@Test
	void mergesAdjacentAndOverlappingRuns()
	{
		PerimeterColumnArea area =
			area(List.of(new PerimeterScanline(0, 0, 4),
				new PerimeterScanline(0, 5, 9),
				new PerimeterScanline(0, 20, 24),
				new PerimeterScanline(0, 22, 30)), 0, 0);
		
		assertEquals(21L, area.columnCount());
		assertEquals(2, area.runCount());
		assertTrue(area.containsXZ(0, 0));
		assertTrue(area.containsXZ(9, 0));
		assertFalse(area.containsXZ(10, 0));
		assertTrue(area.containsXZ(21, 0));
		assertTrue(area.containsXZ(30, 0));
		assertFalse(area.containsXZ(31, 0));
	}
	
	@Test
	void keepsSeparateRunsInTheSameRow()
	{
		PerimeterColumnArea area =
			area(List.of(new PerimeterScanline(3, 0, 1),
				new PerimeterScanline(3, 10, 11)), -1, 1);
		
		assertEquals(4L, area.columnCount());
		assertTrue(area.containsXZ(0, 3));
		assertTrue(area.containsXZ(11, 3));
		assertFalse(area.containsXZ(5, 3));
		assertFalse(area.containsXZ(0, 4));
	}
	
	@Test
	void iteratesColumnsByAscendingZThenAscendingX()
	{
		PerimeterColumnArea area =
			area(List.of(new PerimeterScanline(7, 0, 1),
				new PerimeterScanline(5, 2, 3)), 0, 0);
		
		int[] out = new int[2];
		List<String> columns = new ArrayList<>();
		
		for(long index = 0; index < area.columnCount(); index++)
		{
			assertTrue(area.columnAt(index, out));
			columns.add(out[0] + "," + out[1]);
		}
		
		assertEquals(List.of("2,5", "3,5", "0,7", "1,7"), columns);
		assertFalse(area.columnAt(4, out));
		assertFalse(area.columnAt(-1, out));
	}
	
	@Test
	void iteratesAllColumnsOfSeparatedRuns()
	{
		PerimeterColumnArea area =
			area(List.of(new PerimeterScanline(0, 0, 1),
				new PerimeterScanline(0, 5, 6),
				new PerimeterScanline(1, 0, 0)), 0, 0);
		
		int[] out = new int[2];
		List<String> columns = new ArrayList<>();
		
		for(long index = 0; index < area.columnCount(); index++)
		{
			area.columnAt(index, out);
			columns.add(out[0] + "," + out[1]);
		}
		
		assertEquals(List.of("0,0", "1,0", "5,0", "6,0", "0,1"), columns);
	}
	
	@Test
	void matchesARectangleImplementedAsOneRunPerRow()
	{
		List<PerimeterScanline> scanlines = new ArrayList<>();
		
		for(int z = -4; z <= 4; z++)
			scanlines.add(new PerimeterScanline(z, -4, 4));
		
		PerimeterColumnArea irregular = area(scanlines, -59, 79);
		PerimeterArea rectangle = PerimeterArea.fromXZ(-4, -4, 4, 4, -59, 79);
		
		assertEquals(rectangle.columnCount(), irregular.columnCount());
		assertEquals(rectangle.estimatedBlockCount(),
			irregular.estimatedBlockCount());
		
		int[] irregularOut = new int[2];
		int[] rectangleOut = new int[2];
		
		for(long index = 0; index < rectangle.columnCount(); index++)
		{
			assertTrue(irregular.columnAt(index, irregularOut));
			assertTrue(rectangle.columnAt(index, rectangleOut));
			assertEquals(rectangleOut[0], irregularOut[0]);
			assertEquals(rectangleOut[1], irregularOut[1]);
		}
		
		for(int x = -5; x <= 5; x++)
			for(int z = -5; z <= 5; z++)
				assertEquals(rectangle.containsXZ(x, z),
					irregular.containsXZ(x, z));
	}
	
	@Test
	void estimatedBlockCountMultipliesColumnsByLayers()
	{
		PerimeterColumnArea area =
			area(List.of(new PerimeterScanline(0, 0, 9)), 0, 3);
		
		assertEquals(40L, area.estimatedBlockCount());
		assertEquals(4, area.sizeY());
	}
	
	@Test
	void rejectsAnEmptyArea()
	{
		assertThrows(IllegalArgumentException.class,
			() -> area(List.of(), 0, 1));
	}
	
	@Test
	void rejectsAnInvertedYRange()
	{
		assertThrows(IllegalArgumentException.class,
			() -> area(List.of(new PerimeterScanline(0, 0, 1)), 5, 4));
	}
	
	@Test
	void rejectsAnInvalidScanline()
	{
		assertThrows(IllegalArgumentException.class,
			() -> area(List.of(new PerimeterScanline(0, 5, 1)), 0, 1));
	}
	
	private static PerimeterColumnArea area(List<PerimeterScanline> scanlines,
		int minY, int maxY)
	{
		PerimeterDetectedArea config = new PerimeterDetectedArea();
		config.scanlines = new ArrayList<>(scanlines);
		return new PerimeterColumnArea(config, minY, maxY);
	}
}
