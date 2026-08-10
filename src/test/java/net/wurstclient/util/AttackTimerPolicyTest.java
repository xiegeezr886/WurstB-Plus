package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AttackTimerPolicyTest
{
	@Test
	void computesConfiguredAndRandomizedDelay()
	{
		assertEquals(100_000_000L,
			AttackTimerPolicy.delayNanos(10, 0, 100));
		assertEquals(150_000_000L,
			AttackTimerPolicy.delayNanos(10, 0.5, 100));
		assertEquals(0, AttackTimerPolicy.delayNanos(10, -2, 100));
	}

	@Test
	void sanitizesNonFiniteInputs()
	{
		assertEquals(0,
			AttackTimerPolicy.delayNanos(Double.NaN, Double.NaN, Double.NaN));
		assertEquals(Long.MAX_VALUE - 1, AttackTimerPolicy.delayNanos(
			Double.MIN_VALUE, 0, Double.POSITIVE_INFINITY));
	}

	@Test
	void comparesDeadlinesAcrossNanoTimeWraparound()
	{
		long start = Long.MAX_VALUE - 5;
		long deadline = AttackTimerPolicy.deadline(start, 10);
		assertFalse(AttackTimerPolicy.isElapsed(start, deadline));
		assertTrue(AttackTimerPolicy.isElapsed(Long.MIN_VALUE + 5, deadline));
	}
}
