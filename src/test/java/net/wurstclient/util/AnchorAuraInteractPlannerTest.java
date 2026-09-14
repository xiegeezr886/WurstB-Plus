package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AnchorAuraInteractPlannerTest
{
	/**
	 * 玩家站在 (5,10,5)、眼睛 (5,11.62,5)、range = 6：候选 (0,11,9) 通过旧的
	 * 角点粗筛（40.51 &lt;= (6+0.5)^2），但最近的面心距离平方是 36.26 &gt; 36。
	 */
	@Test
	void rejectsTheFaceThatTheOldCornerFilterLetThrough()
	{
		assertFalse(AnchorAuraInteractPlanner.canClickFace(36.26, 40.51, 36));
		assertTrue(AnchorAuraInteractPlanner.canClickFace(35.99, 40.51, 36));
	}

	@Test
	void rejectsClickFacesThatPointAwayFromThePlayer()
	{
		// 面心不比方块中心更近 = 这个面背对玩家
		assertFalse(AnchorAuraInteractPlanner.canClickFace(30, 25, 36));
		// 边界：正好相等也不算朝向玩家，与原实现的 `>=` 跳过一致
		assertFalse(AnchorAuraInteractPlanner.canClickFace(35, 35, 36));
	}

	@Test
	void appliesTheSameRangeAndFacingRulesToPlacement()
	{
		assertFalse(AnchorAuraInteractPlanner.canPlaceFace(36.26, 30, 40.51, 36));
		assertTrue(AnchorAuraInteractPlanner.canPlaceFace(30, 40.51, 40.51, 36));
		// 相邻方块中心比本方块中心更近 = 背对玩家的面
		assertFalse(AnchorAuraInteractPlanner.canPlaceFace(30, 39, 40.51, 36));
	}

	/**
	 * 旧行为：{@code Stream.max} 在 {10, 9} 上返回下标 0，而它点不到，于是每 tick
	 * 都重新选中它并失败。新行为：跳过它，用下标 1。
	 */
	@Test
	void skipsTheUnreachableTopScoringCandidate()
	{
		assertEquals(1, AnchorAuraInteractPlanner.findBestUsable(
			new double[]{10, 9}, new boolean[]{false, true}));
	}

	@Test
	void keepsTheFirstCandidateOnTies()
	{
		assertEquals(0, AnchorAuraInteractPlanner.findBestUsable(
			new double[]{10, 10}, new boolean[]{true, true}));
		assertEquals(1, AnchorAuraInteractPlanner.findBestUsable(
			new double[]{10, 10}, new boolean[]{false, true}));
	}

	@Test
	void returnsMinusOneWhenNothingIsUsable()
	{
		assertEquals(-1, AnchorAuraInteractPlanner.findBestUsable(
			new double[]{10, 9}, new boolean[]{false, false}));
		assertEquals(-1, AnchorAuraInteractPlanner.findBestUsable(new double[0],
			new boolean[0]));
	}

	@Test
	void rejectsMismatchedArrays()
	{
		assertThrows(IllegalArgumentException.class,
			() -> AnchorAuraInteractPlanner.findBestUsable(new double[]{1},
				new boolean[0]));
	}
}
