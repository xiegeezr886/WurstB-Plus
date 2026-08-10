package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

final class CombatClickSchedulerTest
{
	@Test
	void enforcesClickAfterOneSecond()
	{
		CombatClickScheduler scheduler =
			new CombatClickScheduler(new Random(7));
		scheduler.reset(1, 1, ClickPattern.STABILIZED, 2, 2, 0);
		scheduler.advanceTick();

		assertEquals(0, scheduler.getClickAmount(tick -> false, 999));
		assertEquals(1, scheduler.getClickAmount(tick -> false, 1_000));
	}

	@Test
	void cooldownCanEnforceImmediateClick()
	{
		CombatClickScheduler scheduler =
			new CombatClickScheduler(new Random(1));
		scheduler.reset(1, 1, ClickPattern.STABILIZED, 1, 1, 0);
		scheduler.advanceTick();

		assertEquals(1, scheduler.getClickAmount(tick -> true, 50));
	}

	@Test
	void successfulClicksUpdateAmountAndCooldown()
	{
		CombatClickScheduler scheduler =
			new CombatClickScheduler(new Random(3));
		scheduler.reset(5, 8, ClickPattern.STABILIZED, 0.4F, 0.8F, 0);
		scheduler.beginClickTick();
		scheduler.recordSuccessfulClick(100);
		scheduler.recordSuccessfulClick(100);

		assertEquals(2, scheduler.getExecutedClickAmount());
		assertEquals(0, scheduler.getTicksSinceLastClick());
		assertTrue(scheduler.getNextCooldown() >= 0.4F);
		assertTrue(scheduler.getNextCooldown() <= 0.8F);
	}

	@Test
	void resetTimingPreventsAnIdleWatchdogClick()
	{
		CombatClickScheduler scheduler =
			new CombatClickScheduler(new Random(2));
		scheduler.reset(1, 1, ClickPattern.STABILIZED, 2, 2, 1_000);
		scheduler.advanceTick();
		scheduler.resetTiming(2_000);

		assertEquals(0, scheduler.getClickAmount(tick -> false, 2_999));
		assertEquals(1, scheduler.getClickAmount(tick -> false, 3_000));
	}

	@Test
	void searchesTheWholeBufferedWindowForNextClick()
	{
		CombatClickScheduler scheduler =
			new CombatClickScheduler(new Random(2));
		scheduler.reset(1, 1, ClickPattern.STABILIZED, 2, 2, 0);
		scheduler.advanceTick();
		scheduler.resetTiming(0);

		assertEquals(19,
			scheduler.getTicksUntilClick(tick -> false, 0));
	}

	@Test
	void handlesClockRollbackAndRejectsInvalidLookahead()
	{
		CombatClickScheduler scheduler =
			new CombatClickScheduler(new Random(2));
		scheduler.reset(1, 1, ClickPattern.STABILIZED, Float.NaN,
			Float.POSITIVE_INFINITY, 1_000);
		scheduler.advanceTick();

		assertEquals(0, scheduler.getClickAmount(tick -> false, 500));
		assertThrows(IllegalArgumentException.class,
			() -> scheduler.getClickAmount(tick -> false, 1_000, -1));
	}

	@Test
	void everyTechniqueProducesValidCycle()
	{
		for(ClickPattern pattern : ClickPattern.values())
		{
			int[] clicks = new int[20];
			pattern.fill(clicks, 8, 8, new Random(3));
			int sum = java.util.Arrays.stream(clicks).sum();
			if(pattern == ClickPattern.DOUBLE_CLICK)
				assertEquals(16, sum, pattern.toString());
			else
				assertEquals(8, sum, pattern.toString());
		}
	}
}
