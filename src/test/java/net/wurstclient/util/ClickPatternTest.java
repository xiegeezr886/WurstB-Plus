package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

final class ClickPatternTest
{
	@Test
	void acceptsEmptyAndShortCycles()
	{
		for(ClickPattern pattern : ClickPattern.values())
			assertDoesNotThrow(() -> pattern.fill(new int[0], 5, 8,
				new Random(1)), pattern.toString());

		int[] shortCycle = new int[1];
		ClickPattern.DRAG.fill(shortCycle, 3, 3, new Random(1));
		assertEquals(3, Arrays.stream(shortCycle).sum());
	}

	@Test
	void clampsNegativeCpsToZero()
	{
		for(ClickPattern pattern : ClickPattern.values())
		{
			int[] clicks = new int[20];
			pattern.fill(clicks, -10, -1, new Random(1));
			assertEquals(0, Arrays.stream(clicks).sum(), pattern.toString());
		}
	}

	@Test
	void efficientPatternSamplesCpsOnlyOnce()
	{
		CountingRandom random = new CountingRandom();
		int[] clicks = new int[20];
		ClickPattern.EFFICIENT.fill(clicks, 5, 8, random);

		assertEquals(1, random.boundedLongCalls);
		assertEquals(5, Arrays.stream(clicks).sum());
	}

	private static final class CountingRandom extends Random
	{
		private int boundedLongCalls;

		@Override
		public long nextLong(long origin, long bound)
		{
			boundedLongCalls++;
			return origin;
		}
	}
}
