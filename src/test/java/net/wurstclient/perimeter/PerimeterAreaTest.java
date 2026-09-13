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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PerimeterAreaTest
{
	@Test
	void normalizesCornerOrder()
	{
		PerimeterArea area = PerimeterArea.of(10, 70, 20, -5, -60, -7);
		
		assertEquals(-5, area.minX());
		assertEquals(-60, area.minY());
		assertEquals(-7, area.minZ());
		
		assertEquals(10, area.maxX());
		assertEquals(70, area.maxY());
		assertEquals(20, area.maxZ());
	}
	
	@Test
	void fromXzBuildsAnInclusiveYRange()
	{
		PerimeterArea area = PerimeterArea.fromXZ(100, 200, 199, 299, -59, 79);
		
		assertEquals(100, area.minX());
		assertEquals(200, area.minZ());
		assertEquals(-59, area.minY());
		
		assertEquals(199, area.maxX());
		assertEquals(299, area.maxZ());
		assertEquals(79, area.maxY());
	}
	
	@Test
	void sizesCountBothEndsInclusively()
	{
		PerimeterArea area = PerimeterArea.of(0, 0, 0, 4, 2, 9);
		
		assertEquals(5, area.sizeX());
		assertEquals(3, area.sizeY());
		assertEquals(10, area.sizeZ());
		
		assertEquals(150L, area.volume());
		assertEquals(50L, area.columnCount());
		assertEquals(3, area.layerCount());
	}
	
	@Test
	void singleBlockAreaHasVolumeOne()
	{
		PerimeterArea area = PerimeterArea.of(7, 8, 9, 7, 8, 9);
		
		assertEquals(1, area.sizeX());
		assertEquals(1, area.sizeY());
		assertEquals(1, area.sizeZ());
		assertEquals(1L, area.volume());
	}
	
	@Test
	void containsIncludesEveryBoundary()
	{
		PerimeterArea area = PerimeterArea.of(0, 0, 0, 4, 2, 9);
		
		assertTrue(area.contains(0, 0, 0));
		assertTrue(area.contains(4, 2, 9));
		assertTrue(area.contains(4, 1, 5));
		
		assertFalse(area.contains(-1, 0, 0));
		assertFalse(area.contains(5, 0, 0));
		assertFalse(area.contains(0, -1, 0));
		assertFalse(area.contains(0, 3, 0));
		assertFalse(area.contains(0, 0, -1));
		assertFalse(area.contains(0, 0, 10));
	}
	
	@Test
	void containsColumnIgnoresY()
	{
		PerimeterArea area = PerimeterArea.of(0, 0, 0, 4, 2, 9);
		
		assertTrue(area.containsColumn(0, 0));
		assertTrue(area.containsColumn(4, 9));
		
		assertFalse(area.containsColumn(5, 0));
		assertFalse(area.containsColumn(0, 10));
	}
	
	@Test
	void withYRangeKeepsTheXzRegion()
	{
		PerimeterArea area = PerimeterArea.of(0, 0, 0, 4, 2, 9);
		PerimeterArea narrowed = area.withYRange(-59, -1);
		
		assertEquals(0, narrowed.minX());
		assertEquals(4, narrowed.maxX());
		assertEquals(0, narrowed.minZ());
		assertEquals(9, narrowed.maxZ());
		
		assertEquals(-59, narrowed.minY());
		assertEquals(-1, narrowed.maxY());
		assertEquals(5L * 59L * 10L, narrowed.volume());
	}
	
	@Test
	void largeVolumeDoesNotOverflowAnInt()
	{
		PerimeterArea area = PerimeterArea.of(0, 0, 0, 2999, 999, 2999);
		
		assertEquals(3000L * 1000L * 3000L, area.volume());
		assertTrue(area.volume() > Integer.MAX_VALUE);
	}
	
	@Test
	void equalityIsValueBased()
	{
		PerimeterArea one = PerimeterArea.of(1, 2, 3, 4, 5, 6);
		PerimeterArea same = PerimeterArea.of(1, 2, 3, 4, 5, 6);
		PerimeterArea other = PerimeterArea.of(1, 2, 3, 4, 5, 7);
		
		assertEquals(one, same);
		assertEquals(one.hashCode(), same.hashCode());
		assertNotEquals(one, other);
	}
	
	@Test
	void describeListsBoundsAndVolume()
	{
		PerimeterArea area = PerimeterArea.of(0, 0, 0, 4, 2, 9);
		String description = area.describe();
		
		assertTrue(description.contains("X 0..4"));
		assertTrue(description.contains("Z 0..9"));
		assertTrue(description.contains("Y 0..2"));
		assertTrue(description.contains("5x3x10"));
		assertTrue(description.contains("150"));
	}
}
