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

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

final class PerimeterCursorTest
{
	@Test
	void visitsEveryBlockExactlyOnce()
	{
		PerimeterArea area = PerimeterArea.of(0, 0, 0, 2, 1, 3);
		PerimeterCursor cursor = new PerimeterCursor(area);
		Set<String> seen = new HashSet<>();
		
		assertEquals(24L, cursor.total());
		
		while(cursor.hasNext())
		{
			assertTrue(area.contains(cursor.x(), cursor.y(), cursor.z()));
			seen.add(cursor.x() + "," + cursor.y() + "," + cursor.z());
			cursor.advance();
		}
		
		assertEquals(24, seen.size());
		assertEquals(24L, cursor.index());
		assertFalse(cursor.hasNext());
	}
	
	@Test
	void visitsOnlyBlocksInsideTheAreaForReversedCorners()
	{
		PerimeterArea area = PerimeterArea.of(10, 20, 30, 8, 18, 28);
		PerimeterCursor cursor = new PerimeterCursor(area);
		
		assertEquals(3L * 3L * 3L, cursor.total());
		
		while(cursor.hasNext())
		{
			assertTrue(area.contains(cursor.x(), cursor.y(), cursor.z()));
			cursor.advance();
		}
	}
	
	@Test
	void startsInTheTopLayer()
	{
		PerimeterArea area = PerimeterArea.of(0, 10, 0, 1, 12, 1);
		PerimeterCursor cursor = new PerimeterCursor(area);
		
		assertEquals(12, cursor.y());
		assertEquals(0, cursor.x());
		assertEquals(0, cursor.z());
	}
	
	@Test
	void walksXThenZBeforeDroppingALayer()
	{
		PerimeterArea area = PerimeterArea.of(0, 0, 0, 1, 1, 1);
		PerimeterCursor cursor = new PerimeterCursor(area);
		
		// top layer, four columns, X fastest
		assertEquals("0,1,0", position(cursor));
		cursor.advance();
		assertEquals("1,1,0", position(cursor));
		cursor.advance();
		assertEquals("0,1,1", position(cursor));
		cursor.advance();
		assertEquals("1,1,1", position(cursor));
		
		// the whole layer is done before the next one starts
		cursor.advance();
		assertEquals("0,0,0", position(cursor));
		assertEquals(0, cursor.y());
	}
	
	@Test
	void progressRunsFromZeroToOne()
	{
		PerimeterArea area = PerimeterArea.of(0, 0, 0, 1, 1, 1);
		PerimeterCursor cursor = new PerimeterCursor(area);
		
		assertEquals(0.0, cursor.progress());
		
		cursor.advance(4);
		assertEquals(0.5, cursor.progress());
		
		cursor.advance(4);
		assertEquals(1.0, cursor.progress());
	}
	
	@Test
	void resetRewindsToTheStart()
	{
		PerimeterArea area = PerimeterArea.of(0, 10, 0, 1, 12, 1);
		PerimeterCursor cursor = new PerimeterCursor(area);
		
		cursor.advance(5);
		assertEquals(5L, cursor.index());
		
		cursor.reset();
		assertEquals(0L, cursor.index());
		assertEquals(12, cursor.y());
		assertEquals(0, cursor.x());
		assertEquals(0, cursor.z());
	}
	
	@Test
	void advanceClampsAtTheEnd()
	{
		PerimeterArea area = PerimeterArea.of(0, 0, 0, 1, 1, 1);
		PerimeterCursor cursor = new PerimeterCursor(area);
		
		cursor.advance(9999);
		
		assertEquals(8L, cursor.index());
		assertFalse(cursor.hasNext());
		assertEquals(1.0, cursor.progress());
	}
	
	@Test
	void rejectsNegativeSteps()
	{
		PerimeterCursor cursor =
			new PerimeterCursor(PerimeterArea.of(0, 0, 0, 1, 1, 1));
		
		assertThrows(IllegalArgumentException.class, () -> cursor.advance(-1));
	}
	
	@Test
	void exhaustedCursorRejectsCoordinates()
	{
		PerimeterCursor cursor =
			new PerimeterCursor(PerimeterArea.of(0, 0, 0, 0, 0, 0));
		
		cursor.advance();
		
		assertThrows(IllegalStateException.class, cursor::x);
		assertThrows(IllegalStateException.class, cursor::y);
		assertThrows(IllegalStateException.class, cursor::z);
	}
	
	private String position(PerimeterCursor cursor)
	{
		return cursor.x() + "," + cursor.y() + "," + cursor.z();
	}
}
