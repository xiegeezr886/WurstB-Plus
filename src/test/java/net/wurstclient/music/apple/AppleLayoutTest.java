package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AppleLayoutTest
{
	@Test
	void emptyOrInvalidFocusProducesZeroScrollBounds()
	{
		AppleLayout layout = new AppleLayout();
		layout.beginFrame(400, 0, -1, 0, 0);
		assertEquals(0, layout.minOffset(), 1e-9);
		assertEquals(0, layout.maxOffset(), 1e-9);

		layout.initHeights(3, 40);
		layout.beginFrame(400, -1, -1, 0, 0);
		assertEquals(0, layout.minOffset(), 1e-9);
		assertEquals(0, layout.maxOffset(), 1e-9);
	}

	@Test
	void prefixSumsPlaceLaterLinesBelowEarlierOnes()
	{
		AppleLayout layout = new AppleLayout();
		layout.initHeights(3, 40);
		layout.setLineHeight(0, 40);
		layout.setLineHeight(1, 50);
		layout.setLineHeight(2, 60);
		layout.beginFrame(400, 0, -1, 0, 24);
		layout.commit(400, 0, -1, 0, 24);

		assertEquals(layout.lineY(0) + 40, layout.lineY(1), 1e-9);
		assertEquals(layout.lineY(1) + 50, layout.lineY(2), 1e-9);
		assertTrue(layout.isInViewport(0));
		assertTrue(layout.isBottomLineInViewport());
		assertFalse(layout.hasInterlude());
	}

	@Test
	void interludeShiftsFollowingLinesAndReportsItsOwnY()
	{
		AppleLayout layout = new AppleLayout();
		layout.initHeights(3, 40);
		layout.beginFrame(400, 0, 0, 80, 24);
		layout.commit(400, 0, 0, 80, 24);

		assertTrue(layout.hasInterlude());
		assertEquals(layout.lineY(0) + 40, layout.interludeY(), 1e-9);
		assertEquals(layout.interludeY() + 80, layout.lineY(1), 1e-9);
		assertEquals(layout.lineY(1) + 40, layout.lineY(2), 1e-9);
	}

	@Test
	void outOfRangeHeightUpdatesAreIgnored()
	{
		AppleLayout layout = new AppleLayout();
		layout.initHeights(1, 30);
		layout.setLineHeight(-1, 99);
		layout.setLineHeight(4, 99);
		layout.beginFrame(200, 0, -1, 0, 0);
		layout.commit(200, 0, -1, 0, 0);
		assertEquals(layout.lineY(0) + 30, layout.bottomLineY(), 1e-9);
	}
}
