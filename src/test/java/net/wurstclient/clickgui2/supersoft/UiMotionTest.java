package net.wurstclient.clickgui2.supersoft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UiMotionTest
{
	@Test
	void firstUpdateReturnsTheInitialValue()
	{
		UiMotion motion = new UiMotion(4);
		assertEquals(4, motion.get());
		assertEquals(4, motion.update(12));
	}

	@Test
	void subsequentUpdatesMoveTowardTheTarget() throws InterruptedException
	{
		UiMotion motion = new UiMotion(0, 80, 0.9F);
		motion.update(1);
		Thread.sleep(20);
		float first = motion.update(1);
		assertTrue(first > 0);
		assertTrue(first < 1);
		Thread.sleep(40);
		float later = motion.update(1);
		assertTrue(later >= first);
	}

	@Test
	void snapResetsVelocityAndPosition()
	{
		UiMotion motion = new UiMotion(0);
		motion.snap(0.4F);
		assertEquals(0.4F, motion.get());
		assertEquals(0.4F, motion.update(9));
	}
}
