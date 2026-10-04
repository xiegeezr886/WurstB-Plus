/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the frame clock of an animated background. The renderer uploads a frame
 * whenever this clock moves, so an off-by-one here is a visible stutter, and a
 * missing degenerate-delay rule is an upload every single frame.
 */
final class BackgroundAnimationTest
{
	@Test
	void rejectsAnEmptyAnimation()
	{
		assertThrows(IllegalArgumentException.class,
			() -> new BackgroundAnimation(new int[0], 0));
		assertThrows(IllegalArgumentException.class,
			() -> new BackgroundAnimation(null, 0));
	}

	@Test
	void singleFrameNeverAdvances()
	{
		BackgroundAnimation animation =
			new BackgroundAnimation(new int[]{100}, 0);

		assertEquals(1, animation.frameCount());

		for(int i = 0; i < 5; i++)
			assertEquals(0, animation.frameIndexAt(i * 1_000L));
	}

	/** Browsers show a 0 or 10 ms GIF frame for 100 ms; so do we. */
	@Test
	void degenerateDelaysBecomeTheDefault()
	{
		assertEquals(BackgroundAnimation.DEFAULT_DELAY_MS,
			BackgroundAnimation.sanitize(0));
		assertEquals(BackgroundAnimation.DEFAULT_DELAY_MS,
			BackgroundAnimation.sanitize(10));
		assertEquals(BackgroundAnimation.DEFAULT_DELAY_MS,
			BackgroundAnimation.sanitize(-50));
		assertEquals(20, BackgroundAnimation.sanitize(20));
		assertEquals(BackgroundAnimation.MAX_DELAY_MS,
			BackgroundAnimation.sanitize(999_999));
	}

	@Test
	void walksTheFramesInOrder()
	{
		BackgroundAnimation animation =
			new BackgroundAnimation(new int[]{100, 200, 300}, 0);

		assertEquals(3, animation.frameCount());
		assertEquals(600, animation.totalDurationMs());
		assertTrue(animation.repeatsForever());

		// the boundary belongs to the frame that starts there
		assertEquals(0, animation.frameIndexAt(0));
		assertEquals(0, animation.frameIndexAt(99));
		assertEquals(1, animation.frameIndexAt(100));
		assertEquals(1, animation.frameIndexAt(299));
		assertEquals(2, animation.frameIndexAt(300));
		assertEquals(2, animation.frameIndexAt(599));
	}

	@Test
	void loopsForeverWhenTheFileAsksForIt()
	{
		BackgroundAnimation animation =
			new BackgroundAnimation(new int[]{100, 200, 300}, 0);

		assertEquals(0, animation.frameIndexAt(600));
		assertEquals(1, animation.frameIndexAt(700));
		assertEquals(2, animation.frameIndexAt(14_400 + 300));
	}

	/** A finite animation stops on its last frame instead of restarting. */
	@Test
	void holdsTheLastFrameAfterTheLastLoop()
	{
		BackgroundAnimation animation =
			new BackgroundAnimation(new int[]{100, 100}, 2);

		assertFalse(animation.repeatsForever());
		assertEquals(0, animation.frameIndexAt(0));
		assertEquals(1, animation.frameIndexAt(100));
		assertEquals(0, animation.frameIndexAt(200));
		assertEquals(1, animation.frameIndexAt(300));
		assertEquals(1, animation.frameIndexAt(400));
		assertEquals(1, animation.frameIndexAt(100_000));
	}

	@Test
	void negativeElapsedIsTheFirstFrame()
	{
		BackgroundAnimation animation =
			new BackgroundAnimation(new int[]{100, 100}, 0);

		assertEquals(0, animation.frameIndexAt(-1));
		assertEquals(0, animation.frameIndexAt(Long.MIN_VALUE));
	}

	@Test
	void negativeLoopCountMeansForever()
	{
		BackgroundAnimation animation =
			new BackgroundAnimation(new int[]{100, 100}, -3);

		assertEquals(0, animation.loopCount());
		assertTrue(animation.repeatsForever());
	}
}
