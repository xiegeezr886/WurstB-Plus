package net.wurstclient.hacks.aura;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AuraAttackClockTest
{
	@Test void tenCpsCannotBurstOnFollowingTicks()
	{
		var clock = new AuraAttackClock();
		assertTrue(clock.ready(0, 0, false, 0));
		clock.record(0, 0, 10, 0);
		assertFalse(clock.ready(0, 0, false, 1));
		assertFalse(clock.ready(50, 1, false, 1));
		assertFalse(clock.ready(99, 2, false, 1));
		assertTrue(clock.ready(100, 2, false, 0));
	}

	@Test void jitterIsSampledOnceRatherThanOnEveryPoll()
	{
		var clock = new AuraAttackClock();
		clock.record(1000, 0, 10, 4);
		for(int i = 0; i < 100; i++) assertFalse(clock.ready(1103, 2, false, 1));
		assertTrue(clock.ready(1104, 2, false, 1));
	}

	@Test void delayedTickDoesNotAccumulateCatchUpAttacks()
	{
		var clock = new AuraAttackClock();
		clock.record(0, 0, 10, 0);
		assertTrue(clock.ready(5000, 100, false, 1));
		clock.record(5000, 100, 10, 0);
		assertFalse(clock.ready(5000, 100, false, 1));
		assertFalse(clock.ready(5050, 101, false, 1));
	}

	@Test void modernModeRequiresFullCooldownAndOnlyOneAttackPerTick()
	{
		var clock = new AuraAttackClock();
		assertFalse(clock.ready(10000, 1, true, .99F));
		assertTrue(clock.ready(10000, 1, true, 1));
		clock.record(10000, 1, 1, 0);
		assertFalse(clock.ready(10001, 1, true, 1));
		assertTrue(clock.ready(10050, 2, true, 1));
	}

	@Test void worldResetAllowsFreshTickCounter()
	{
		var clock = new AuraAttackClock();
		clock.record(1000, 1, 1, 0);
		clock.reset();
		assertTrue(clock.ready(1001, 1, false, 1));
	}

	@Test void fastestRateIsBoundedByTheTwentyTickGameLoop()
	{
		var clock = new AuraAttackClock();
		clock.record(0, 0, 100, -100);
		assertFalse(clock.ready(49, 1, false, 1));
		assertTrue(clock.ready(50, 1, false, 1));
	}
}
