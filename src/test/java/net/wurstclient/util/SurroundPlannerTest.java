package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class SurroundPlannerTest
{
	@Test
	void mapsIndexesToLegacyOffsets()
	{
		assertEquals(4, SurroundPlanner.COUNT);
		assertEquals(1, SurroundPlanner.offsetX(0));
		assertEquals(0, SurroundPlanner.offsetZ(0));
		assertEquals(-1, SurroundPlanner.offsetX(1));
		assertEquals(0, SurroundPlanner.offsetZ(1));
		assertEquals(0, SurroundPlanner.offsetX(2));
		assertEquals(1, SurroundPlanner.offsetZ(2));
		assertEquals(0, SurroundPlanner.offsetX(3));
		assertEquals(-1, SurroundPlanner.offsetZ(3));
	}

	@Test
	void keepsLegacyOrderWithoutAThreat()
	{
		assertArrayEquals(new int[]{0, 1, 2, 3},
			SurroundPlanner.plan(0, 0, Double.NaN, Double.NaN, allUsable()));
		assertArrayEquals(new int[]{0, 1, 2, 3},
			SurroundPlanner.plan(0, 0, 0, 0, allUsable()));
	}

	@Test
	void prioritisesTheSideFacingTheThreat()
	{
		// 敌人在正东：先东侧，再两侧，背后的西侧最后
		assertArrayEquals(new int[]{0, 2, 3, 1},
			SurroundPlanner.plan(0, 0, 5, 0, allUsable()));

		// 敌人在正北：先北侧，再两侧，背后的南侧最后
		assertArrayEquals(new int[]{3, 0, 1, 2},
			SurroundPlanner.plan(0, 0, 0, -5, allUsable()));

		// 敌人在东北：东和北同分，保持原顺序
		assertArrayEquals(new int[]{0, 3, 1, 2},
			SurroundPlanner.plan(0, 0, 5, -5, allUsable()));

		// 敌人在西南：西和南同分，保持原顺序
		assertArrayEquals(new int[]{1, 2, 0, 3},
			SurroundPlanner.plan(0, 0, -5, 5, allUsable()));
	}

	@Test
	void skipsUnusablePositions()
	{
		boolean[] onlyWestAndNorth = {false, true, false, true};

		assertArrayEquals(new int[]{1, 3}, SurroundPlanner.plan(0, 0,
			Double.NaN, Double.NaN, onlyWestAndNorth));

		// 敌人虽然在正东，但东侧已经被占住，剩下北侧比西侧更靠前
		assertArrayEquals(new int[]{3, 1}, SurroundPlanner.plan(0, 0, 5, 0,
			onlyWestAndNorth));

		// 只有西侧不能放
		assertArrayEquals(new int[]{0, 2, 3},
			SurroundPlanner.plan(0, 0, 5, 0, new boolean[]{true, false, true,
				true}));
	}

	private static boolean[] allUsable()
	{
		return new boolean[]{true, true, true, true};
	}
}
