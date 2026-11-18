package net.wurstclient.hud2.target;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class TargetHudMotionTest
{
	@Test void expiresAfterOneSecondAndClosesOverFourHundredMillis()
	{
		assertTrue(TargetHudMotion.shouldShow(999, 0, true));
		assertFalse(TargetHudMotion.shouldShow(1000, 0, true));
		assertFalse(TargetHudMotion.shouldShow(500, 0, false));
		TargetHudMotion motion = new TargetHudMotion();
		motion.setVisible(true, 0);
		assertEquals(0, motion.scale(0));
		assertTrue(motion.scale(100) > 1); // Elastic overshoot.
		assertEquals(1, motion.scale(850));
		motion.setVisible(false, 1000);
		assertTrue(motion.scale(1200) > 0);
		assertEquals(0, motion.scale(1400));
	}

	@Test void reversingAnExitDoesNotJump()
	{
		TargetHudMotion motion = new TargetHudMotion();
		motion.setVisible(true, 0);
		motion.setVisible(false, 900);
		float before = motion.scale(1200);
		motion.setVisible(true, 1200);
		assertEquals(before, motion.scale(1200));
		assertEquals(1, motion.scale(2050));
	}

	@Test void healthRetargetsContinuouslyAndNewEntityResetsIt()
	{
		TargetHudMotion motion = new TargetHudMotion();
		motion.setHealth(20, 20, true, 0);
		assertEquals(0, motion.health(0));
		assertEquals(1, motion.health(250));
		motion.setHealth(10, 20, false, 250);
		assertEquals(1, motion.health(250));
		float middle = motion.health(350);
		assertTrue(middle > .5F && middle < 1);
		motion.setHealth(2, 20, false, 350);
		assertEquals(middle, motion.health(350));
		assertEquals(.1F, motion.health(600), .0001);
		motion.setHealth(15, 20, true, 600);
		assertEquals(0, motion.health(600));
		assertEquals(.75F, motion.health(850));
		motion.setHealth(Float.NaN, 20, true, 900);
		assertEquals(0, motion.health(1150));
		motion.setHealth(30, 20, true, 1200);
		assertEquals(1, motion.health(1450));
		motion.setHealth(10, 0, true, 1500);
		assertEquals(0, motion.health(1750));
	}
}
