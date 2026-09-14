package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class AutoTrapPlannerTest
{
	@Test
	void mapsIndexesToTheLegacyTrapOffsets()
	{
		assertEquals(12, AutoTrapPlanner.COUNT);

		int[][] legacy = {
			{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
			{1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
			{1, 2, 0}, {-1, 2, 0}, {0, 2, 1}, {0, 2, -1},
		};

		for(int i = 0; i < AutoTrapPlanner.COUNT; i++)
		{
			assertEquals(legacy[i][0], AutoTrapPlanner.offsetX(i));
			assertEquals(legacy[i][1], AutoTrapPlanner.offsetY(i));
			assertEquals(legacy[i][2], AutoTrapPlanner.offsetZ(i));
		}
	}

	@Test
	void filtersUnusablePositions()
	{
		assertArrayEquals(new int[0],
			AutoTrapPlanner.plan(new boolean[AutoTrapPlanner.COUNT],
				new double[AutoTrapPlanner.COUNT], 0));

		int[] usableIndexes = {1, 4, 6, 10};
		double[] equal = new double[AutoTrapPlanner.COUNT];

		// 目标朝 +Z 看：可用的 +Z 两格排前面（dy=2 那格最前），朝 -X 的
		// dy=1 那格最后
		assertArrayEquals(new int[]{10, 6, 1, 4},
			AutoTrapPlanner.plan(usable(usableIndexes), equal, 0));
	}

	@Test
	void prefersThePositionsThatActuallySealTheTarget()
	{
		double[] equal = new double[AutoTrapPlanner.COUNT];

		// 目标不动（朝向未知）、十二格等距：脚下 dy=0 与头顶 dy=2 各自都能
		// 独立把它钉死，算作同一档，档内保持旧版位置表先后（不会因为排序
		// 而来回跳）；dy=1 那一圈被目标自己的碰撞箱占着，排最后
		assertArrayEquals(new int[]{0, 1, 2, 3, 8, 9, 10, 11, 4, 5, 6, 7},
			plan(Double.NaN, equal));
	}

	@Test
	void prioritisesTheSideTheTargetIsLookingTowards()
	{
		double[] equal = new double[AutoTrapPlanner.COUNT];

		// yaw=0 朝 +Z（南）：先南侧，再东西两侧，背后的北侧最后
		assertArrayEquals(new int[]{2, 10, 6, 0, 1, 8, 9, 4, 5, 3, 11, 7},
			plan(0, equal));

		// yaw=90 朝 -X（西）
		assertArrayEquals(new int[]{1, 9, 5, 2, 10, 3, 11, 6, 7, 0, 8, 4},
			plan(90, equal));

		// yaw=180 朝 -Z（北）
		assertArrayEquals(new int[]{3, 11, 7, 1, 9, 0, 8, 5, 4, 2, 10, 6},
			plan(180, equal));

		// yaw=270 朝 +X（东）
		assertArrayEquals(new int[]{0, 8, 4, 3, 11, 2, 10, 7, 6, 1, 9, 5},
			plan(270, equal));
	}

	@Test
	void breaksDistanceTiesInsideTheSameGroup()
	{
		int[] usableIndexes = {1, 6, 7, 8, 10};
		double[] distanceSq = new double[AutoTrapPlanner.COUNT];

		// 只有这些位置可用，且 dy=2 那一格里 +Z 位置最近、+X 位置最远；
		// dy=1 的 +Z 位置其实比 +X 的 dy=2 位置更近，但那是低一档，越不过去
		distanceSq[10] = 0.5;
		distanceSq[8] = 4;
		distanceSq[7] = 12.25;
		distanceSq[6] = 12.05;
		distanceSq[1] = 25;

		assertArrayEquals(new int[]{10, 8, 1, 6, 7},
			AutoTrapPlanner.plan(usable(usableIndexes), distanceSq,
				Double.NaN));
	}

	private static int[] plan(double targetYaw, double[] distanceSq)
	{
		return AutoTrapPlanner.plan(usable(null), distanceSq, targetYaw);
	}

	private static boolean[] usable(int[] indexes)
	{
		boolean[] usable = new boolean[AutoTrapPlanner.COUNT];
		if(indexes == null)
		{
			for(int i = 0; i < AutoTrapPlanner.COUNT; i++)
				usable[i] = true;
			return usable;
		}

		for(int i : indexes)
			usable[i] = true;
		return usable;
	}
}
