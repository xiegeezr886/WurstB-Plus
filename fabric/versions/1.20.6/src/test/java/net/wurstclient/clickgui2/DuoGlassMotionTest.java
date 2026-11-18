package net.wurstclient.clickgui2;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class DuoGlassMotionTest
{
	@Test void opensAndClosesWithExactEndpoints()
	{
		var motion = new DuoGlassMotion(0);
		assertEquals(1, motion.value(0));
		assertFalse(motion.ready(0));
		assertTrue(motion.ready(400));
		motion.close(500);
		assertEquals(0, motion.value(500));
		assertFalse(motion.ready(500));
		assertFalse(motion.finished(600));
		assertTrue(motion.finished(900));
	}

	@Test void closeDuringOpeningRetainsTheCurrentAngle()
	{
		var motion = new DuoGlassMotion(0);
		float before = motion.value(100);
		motion.close(100);
		assertEquals(before, motion.value(100));
		assertTrue(motion.value(150) > before);
		assertTrue(motion.finished(500));
	}

	@Test void smoothingIsIndependentOfFrameRate()
	{
		var fast = new DuoGlassMotion(0);
		var slow = new DuoGlassMotion(0);
		for(int time = 5; time <= 100; time += 5) fast.value(time);
		assertEquals(slow.value(100), fast.value(100), .000001);
		assertEquals(Math.pow(.78, 6), fast.value(100), .000001);
	}
}
