package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class CityBlockPlannerTest
{
	@Test
	void mapsIndexesToLegacyOffsets()
	{
		assertEquals(4, CityBlockPlanner.COUNT);
		assertEquals(1, CityBlockPlanner.offsetX(0));
		assertEquals(0, CityBlockPlanner.offsetZ(0));
		assertEquals(-1, CityBlockPlanner.offsetX(1));
		assertEquals(0, CityBlockPlanner.offsetZ(1));
		assertEquals(0, CityBlockPlanner.offsetX(2));
		assertEquals(1, CityBlockPlanner.offsetZ(2));
		assertEquals(0, CityBlockPlanner.offsetX(3));
		assertEquals(-1, CityBlockPlanner.offsetZ(3));
	}

	@Test
	void keepsClosestFirstWhenNoWallIsPunishable()
	{
		// 一面墙都换不来水晶位：退回原来的"离玩家最近"行为
		assertArrayEquals(new int[]{2, 0, 1, 3}, CityBlockPlanner.plan(
			new double[]{4, 9, 1, 16}, allUsable(), nonePunishable()));
	}

	@Test
	void prefersTheWallThatOpensACrystalSpot()
	{
		// 3 号最远，但只有它挖掉之后目标脚边会出现水晶位
		assertArrayEquals(new int[]{3, 2, 0, 1}, CityBlockPlanner.plan(
			new double[]{4, 9, 1, 16}, allUsable(),
			new boolean[]{false, false, false, true}));

		// 0 号和 3 号都能换来水晶位：两者之间再按距离排
		assertArrayEquals(new int[]{0, 3, 2, 1}, CityBlockPlanner.plan(
			new double[]{4, 9, 1, 16}, allUsable(),
			new boolean[]{true, false, false, true}));
	}

	@Test
	void keepsOriginalOrderOnTies()
	{
		// 距离与优先级全都一样：保持 东→西→南→北，结果确定
		assertArrayEquals(new int[]{0, 1, 2, 3}, CityBlockPlanner.plan(
			new double[]{1, 1, 1, 1}, allUsable(), nonePunishable()));

		// 优先级相同、距离并列：同样保持原下标顺序
		assertArrayEquals(new int[]{1, 3, 0, 2}, CityBlockPlanner.plan(
			new double[]{4, 1, 4, 1}, allUsable(),
			new boolean[]{false, true, false, true}));
	}

	@Test
	void filtersOutUnusableWalls()
	{
		// 只有西侧和北侧能挖，距离排序照旧
		assertArrayEquals(new int[]{1, 3}, CityBlockPlanner.plan(
			new double[]{4, 9, 1, 16}, new boolean[]{false, true, false, true},
			nonePunishable()));

		// 只有不能挖的西侧能换来水晶位：先被过滤掉，剩下仍按距离排
		assertArrayEquals(new int[]{2, 0, 3}, CityBlockPlanner.plan(
			new double[]{4, 9, 1, 16}, new boolean[]{true, false, true, true},
			new boolean[]{false, true, false, false}));

		// 南侧不能挖
		assertArrayEquals(new int[]{0, 1, 3}, CityBlockPlanner.plan(
			new double[]{4, 9, 1, 16}, new boolean[]{true, true, false, true},
			nonePunishable()));
	}

	@Test
	void handlesEmptyAndShortInput()
	{
		assertArrayEquals(new int[0], CityBlockPlanner.plan(new double[0],
			new boolean[0], new boolean[0]));

		// 长度不一致时按最短的算，不越界
		assertArrayEquals(new int[0], CityBlockPlanner.plan(new double[]{1, 2},
			new boolean[0], new boolean[]{true, true}));

		assertArrayEquals(new int[]{1, 0}, CityBlockPlanner.plan(
			new double[]{5, 1, 3}, new boolean[]{true, true, true},
			new boolean[]{false, true}));
	}

	private static boolean[] allUsable()
	{
		return new boolean[]{true, true, true, true};
	}

	private static boolean[] nonePunishable()
	{
		return new boolean[]{false, false, false, false};
	}
}
