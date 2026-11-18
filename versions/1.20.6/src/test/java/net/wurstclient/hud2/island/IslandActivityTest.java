package net.wurstclient.hud2.island;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class IslandActivityTest
{
	@Test void miningSurvivesBetweenTicksButAbandonedWorkExpires()
	{
		IslandActivity activity = new IslandActivity();
		assertTrue(activity.mine(123, "Bed", .2F, 0));
		assertFalse(activity.mine(123, "Bed", .3F, 50));
		assertEquals(.3F, activity.mining(199).progress());
		assertNull(activity.mining(200));
		assertTrue(activity.mine(123, "Bed", .1F, 250));
	}

	@Test void differentTargetOrProgressResetRestartsProgressAnimation()
	{
		IslandActivity activity = new IslandActivity();
		activity.mine(1, "Stone", .8F, 0);
		assertTrue(activity.mine(2, "Bed", .1F, 50));
		assertEquals("Bed", activity.mining(50).name());
		assertFalse(activity.mine(2, "Bed", .5F, 60));
		assertTrue(activity.mine(2, "Bed", .1F, 70));
		activity.mine(2, "Bed", 1, 100);
		assertEquals(1, activity.mining(549).progress());
		assertNull(activity.mining(550));
	}

	@Test void invalidProgressDoesNotPoisonAnimationAndResetDropsWorldState()
	{
		IslandActivity activity = new IslandActivity();
		assertFalse(activity.mine(1, "Stone", Float.NaN, 0));
		assertFalse(activity.mine(1, "Stone", Float.POSITIVE_INFINITY, 0));
		assertNull(activity.mining(0));
		activity.mine(1, "Stone", 5, 1);
		assertEquals(1, activity.mining(1).progress());
		activity.container(new Object(), 3, 1);
		activity.reset();
		assertNull(activity.mining(2));
		assertEquals(0, activity.rows(2));
	}

	@Test void closeRetainsContainerFeedbackWithoutExtendingItsDeadline()
	{
		IslandActivity activity = new IslandActivity();
		Object menu = new Object();
		assertTrue(activity.container(menu, 6, 0));
		assertFalse(activity.container(menu, 6, 50));
		activity.container(null, 0, 100);
		activity.container(null, 0, 200);
		assertEquals(6, activity.rows(549));
		assertEquals(0, activity.rows(550));
		assertTrue(activity.container(new Object(), 3, 551));
		assertEquals(3, activity.rows(551));
	}

	@Test void replacementMenuDoesNotInheritPreviousSlotFeedback()
	{
		IslandActivity activity = new IslandActivity();
		Object first = new Object(), second = new Object();
		activity.container(first, 3, 0);
		assertTrue(activity.container(second, 6, 20));
		assertEquals(6, activity.rows(20));
		activity.container(null, 0, 30);
		assertTrue(activity.container(second, 6, 40));
		assertFalse(activity.container(second, 6, 50));
	}
}
