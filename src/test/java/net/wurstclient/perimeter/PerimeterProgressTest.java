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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class PerimeterProgressTest
{
	private static final long TEN_SECONDS = 10_000_000_000L;
	
	@Test
	void beginStartsAtZeroBrokenBlocks()
	{
		PerimeterProgress progress = new PerimeterProgress();
		progress.begin(1000, 0L);
		
		assertTrue(progress.isStarted());
		assertFalse(progress.isFinished());
		assertFalse(progress.isComplete());
		
		assertEquals(1000, progress.total());
		assertEquals(1000, progress.remaining());
		assertEquals(0, progress.broken());
		assertEquals(0.0, progress.fraction());
		assertEquals(0, progress.percent());
	}
	
	@Test
	void tracksBrokenBlocksAndPercentage()
	{
		PerimeterProgress progress = new PerimeterProgress();
		progress.begin(80, 0L);
		progress.updateRemaining(20);
		
		assertEquals(60, progress.broken());
		assertEquals(20, progress.remaining());
		assertEquals(0.75, progress.fraction());
		assertEquals(75, progress.percent());
	}
	
	@Test
	void clampsRemainingToTheTotal()
	{
		PerimeterProgress progress = new PerimeterProgress();
		progress.begin(10, 0L);
		
		progress.updateRemaining(999);
		assertEquals(10, progress.remaining());
		assertEquals(0, progress.broken());
		
		progress.updateRemaining(-5);
		assertEquals(0, progress.remaining());
		assertEquals(10, progress.broken());
		assertEquals(1.0, progress.fraction());
		assertEquals(100, progress.percent());
	}
	
	@Test
	void emptyPlanCountsAsComplete()
	{
		PerimeterProgress progress = new PerimeterProgress();
		progress.begin(0, 0L);
		
		assertEquals(1.0, progress.fraction());
		assertEquals(100, progress.percent());
		assertTrue(progress.isComplete());
	}
	
	@Test
	void finishFreezesElapsedTime()
	{
		PerimeterProgress progress = new PerimeterProgress();
		progress.begin(100, 0L);
		progress.finish(TEN_SECONDS);
		
		assertTrue(progress.isFinished());
		assertTrue(progress.isComplete());
		assertEquals(10_000L, progress.elapsedMillis(TEN_SECONDS * 3));
		assertEquals(0, progress.remaining());
	}
	
	@Test
	void elapsedRunsWhileDigging()
	{
		PerimeterProgress progress = new PerimeterProgress();
		progress.begin(100, 0L);
		
		assertEquals(5_000L, progress.elapsedMillis(TEN_SECONDS / 2));
	}
	
	@Test
	void etaIsUnknownBeforeAnythingIsBroken()
	{
		PerimeterProgress progress = new PerimeterProgress();
		progress.begin(1000, 0L);
		
		assertEquals(-1L, progress.etaMillis(TEN_SECONDS));
	}
	
	@Test
	void etaScalesWithTheObservedRate()
	{
		PerimeterProgress progress = new PerimeterProgress();
		progress.begin(1000, 0L);
		progress.updateRemaining(500);
		
		// 500 blocks in 10 seconds, 500 left, so another 10 seconds
		assertEquals(10_000L, progress.etaMillis(TEN_SECONDS));
	}
	
	@Test
	void etaIsZeroOnceComplete()
	{
		PerimeterProgress progress = new PerimeterProgress();
		progress.begin(100, 0L);
		progress.finish(TEN_SECONDS);
		
		assertEquals(0L, progress.etaMillis(TEN_SECONDS));
	}
	
	@Test
	void resetClearsEverything()
	{
		PerimeterProgress progress = new PerimeterProgress();
		progress.begin(100, 0L);
		progress.updateRemaining(50);
		progress.reset();
		
		assertFalse(progress.isStarted());
		assertFalse(progress.isFinished());
		assertEquals(0, progress.total());
		assertEquals(0, progress.remaining());
		assertEquals(0L, progress.elapsedMillis(TEN_SECONDS));
	}
	
	@Test
	void formatDurationUsesReadableUnits()
	{
		assertEquals("0s", PerimeterProgress.formatDuration(0));
		assertEquals("45s", PerimeterProgress.formatDuration(45_000L));
		assertEquals("3m 05s", PerimeterProgress.formatDuration(185_000L));
		assertEquals("1h 02m 03s",
			PerimeterProgress.formatDuration(3_723_000L));
		assertEquals("unknown", PerimeterProgress.formatDuration(-1));
	}
	
	@Test
	void describeMentionsTheRemainingWork()
	{
		PerimeterProgress progress = new PerimeterProgress();
		progress.begin(10, 0L);
		progress.updateRemaining(4);
		
		String description = progress.describe(TEN_SECONDS);
		
		assertTrue(description.contains("6/10 blocks"));
		assertTrue(description.contains("60%"));
		assertTrue(description.contains("elapsed"));
	}
}
