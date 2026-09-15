/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

/**
 * AnchorAura 的纯几何决策：某个候选方块「能不能点到」的判定，以及「在能点到的
 * 候选里选谁」的排序规则。不引用任何 Minecraft 类型，便于单测。
 *
 * <p>
 * 抽出来的原因是一个真实缺陷：AnchorAura 的粗筛用的是
 * {@code |方块最小角 - 眼睛| <= (range + 0.5)}，而真正点击时要求「某个面的面心到
 * 眼睛 <= range」。两个半径不同，于是存在一批方块能通过粗筛、却没有任何一个面能
 * 点到：玩家站在 (5, 10, 5)（眼睛 (5, 11.62, 5)）、range = 6 时，候选 (0, 11, 9)
 * 的最小角到 (4.5, 11.12, 4.5) 的距离平方是 40.51 &lt;= 42.25 = (6 + 0.5)^2，
 * 通过粗筛；但它六个面的面心到眼睛最近也有 36.26 &gt; 36 = 6^2，所以
 * {@code placeAnchor} / {@code rightClickBlock} 对它永远返回 false。
 *
 * <p>
 * 打分的确定性让这个缺陷不会自愈：分数最高的方块会一直最高。旧实现于是每 tick
 * 重新选中同一个点不到的位置、再次失败，什么也不做；已充能的重生锚落在这一圈里
 * 时更糟——待执行动作每 20 tick 超时后又被重新选中，充能与放置其它锚全部被饿死。
 * 把「能不能点到」并进候选资格后，这些位置会被跳过，改用下一个能点到的候选。
 */
public enum AnchorAuraInteractPlanner
{
	;

	/**
	 * {@code rightClickBlock} 用的面判定：面心必须在 range 内，且必须比方块中心
	 * 更靠近眼睛（即这个面朝向玩家）。
	 */
	public static boolean canClickFace(double eyeToFaceSq,
		double eyeToCenterSq, double rangeSq)
	{
		return eyeToFaceSq <= rangeSq && eyeToFaceSq < eyeToCenterSq;
	}

	/**
	 * {@code placeAnchor} 用的面判定：面心在 range 内，且相邻方块的中心不比本
	 * 方块的中心更远（即这个面朝向玩家）。
	 */
	public static boolean canPlaceFace(double eyeToFaceSq,
		double eyeToNeighborCenterSq, double eyeToCenterSq, double rangeSq)
	{
		return eyeToFaceSq <= rangeSq
			&& eyeToCenterSq <= eyeToNeighborCenterSq;
	}

	/**
	 * 只在 {@code usable[i]} 为真的候选里取分数最大者并返回其下标；一个都没有时
	 * 返回 -1。同分取下标最小者，与原 {@code Stream.max} 的「首个最大值」语义一致，
	 * 所以调用方按原顺序传入候选就能保持原有的同分行为。
	 */
	public static int findBestUsable(double[] scores, boolean[] usable)
	{
		if(scores.length != usable.length)
			throw new IllegalArgumentException(
				"scores and usable must have the same length");

		int best = -1;
		double bestScore = 0;

		for(int i = 0; i < scores.length; i++)
		{
			if(!usable[i])
				continue;

			if(best < 0 || scores[i] > bestScore)
			{
				best = i;
				bestScore = scores[i];
			}
		}

		return best;
	}
}
