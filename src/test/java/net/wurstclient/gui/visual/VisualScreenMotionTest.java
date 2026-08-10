package net.wurstclient.gui.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VisualScreenMotionTest
{
	@Test
	void progressIsClampedToAnimationRange()
	{
		long start = 1_000_000_000L;
		assertEquals(0, VisualScreenMotion.progress(start, start - 1));
		assertEquals(0, VisualScreenMotion.progress(start, start));
		assertEquals(1, VisualScreenMotion.progress(start,
			start + VisualScreenMotion.DURATION_MS * 2_000_000L));
	}

	@Test
	void scaleAndVeilFinishAtRestingValues()
	{
		assertTrue(VisualScreenMotion.scale(0) < 1);
		assertEquals(1, VisualScreenMotion.scale(1));
		assertTrue(VisualScreenMotion.veilColor(0) >>> 24 > 0);
		assertEquals(0, VisualScreenMotion.veilColor(1) >>> 24);
	}

	@Test
	void easingIsMonotonicAndClamped()
	{
		assertEquals(0, VisualScreenMotion.easedProgress(-1));
		assertTrue(VisualScreenMotion.easedProgress(0.5F) > 0.5F);
		assertEquals(1, VisualScreenMotion.easedProgress(2));
	}
}
