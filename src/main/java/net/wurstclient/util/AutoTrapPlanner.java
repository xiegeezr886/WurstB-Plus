/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.Arrays;

/**
 * AutoTrap 十二个候选位置的纯逻辑：谁先放、谁根本不放（排序与过滤）。
 *
 * <p>
 * 位置表与旧版 {@code AutoTrapHack} 里的 {@code trapOffsets} 一一对应，
 * 顺序也照抄（+X → -X → +Z → -Z，底下那圈先写），所以 {@link #plan} 返回的
 * 顺序在同分时与旧行为完全一致。
 *
 * <p>
 * 改动只有一个来源：参考项目 OpenEpsilon 的 AutoTrap 把“先放哪一格”当成
 * 胜负手（{@code module/combat/AutoTrap.kt:137-142}，注释写得很直白——
 * “sort offsetList by optimal caging success factor”），而旧版本这里只按
 * “离玩家最近”挑一格。1.20.1 上真正决定成败的是下面两件事：
 *
 * <p>
 * 1. 目标是一格宽两格高，脚下一圈（dy=0）与头顶一圈（dy=2）各自都能独立
 * 把它钉死：脚下那圈挡住迈步，头顶那圈让它连抬脚都做不到（跳也跳不出去，
 * 脚上方还是空的，所以不用先放脚下）。而 dy=1 那一圈在目标站立时大部分被它
 * 自己的碰撞箱占着，服务端不会让方块真的放上去——旧代码却常常先挑这一圈，
 * 白打一次交互、白等一个 tick。
 *
 * <p>
 * 2. 同一高度上，朝目标正在看的方向（也就是它准备逃的方向）先补，能最早
 * 合上那个缺口。取朝向而不是速度：{@code LivingEntity#getYRot()} 任何实体都
 * 有、不会抖，且零向量不需要额外分支。
 *
 * <p>
 * 本类不引用任何 Minecraft 类型，便于单元测试。
 */
public enum AutoTrapPlanner
{
	;

	public static final int COUNT = 12;

	// 与旧版 AutoTrapHack 的 trapOffsets 逐项对应
	private static final int[] OFFSET_X = {1, -1, 0, 0, 1, -1, 0, 0, 1, -1,
		0, 0};
	private static final int[] OFFSET_Y = {0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2,
		2};
	private static final int[] OFFSET_Z = {0, 0, 1, -1, 0, 0, 1, -1, 0, 0, 1,
		-1};

	public static int offsetX(int index)
	{
		return OFFSET_X[index];
	}

	public static int offsetY(int index)
	{
		return OFFSET_Y[index];
	}

	public static int offsetZ(int index)
	{
		return OFFSET_Z[index];
	}

	/**
	 * 返回真正该放的候选下标，最该先放的排在最前面。
	 *
	 * @param usable
	 *            每个候选位置当前能不能放（可替换、没被实体占住、不与玩家
	 *            自己的碰撞盒重叠），长度为 {@link #COUNT}
	 * @param distanceSq
	 *            每个候选位置到玩家眼睛的平方距离，仅在自身那档分数打平时
	 *            用来分先后；传 {@code null} 视为全部等距
	 * @param targetYaw
	 *            目标的水平朝向（原版 yaw，度），无目标时传
	 *            {@link Double#NaN}
	 * @return 排好序、并且已经剔除不可用候选的下标数组；全不可用时为空数组
	 */
	public static int[] plan(boolean[] usable, double[] distanceSq,
		double targetYaw)
	{
		double[] scores = score(distanceSq, targetYaw);

		int[] order = new int[COUNT];
		int size = 0;
		for(int i = 0; i < COUNT; i++)
			if(usable[i])
				order[size++] = i;

		// 插入排序：分数高的在前。分数相同时不交换，于是保持位置表原本的
		// +X → -X → +Z → -Z 顺序，结果才是确定的。
		for(int i = 1; i < size; i++)
		{
			int current = order[i];
			int j = i - 1;
			while(j >= 0 && scores[order[j]] < scores[current])
			{
				order[j + 1] = order[j];
				j--;
			}
			order[j + 1] = current;
		}

		return Arrays.copyOf(order, size);
	}

	/**
	 * 单个候选的优先级分数，用三层权重保证判定顺序是严格的：
	 *
	 * <p>
	 * 1. 逃逸对齐度：候选方向与“目标看的方向”的单位向量点积，落在 [-1,1]，
	 * 权重 {@code ALIGN_WEIGHT=4}，于是对齐度最多只能压过两档高度。
	 *
	 * <p>
	 * 2. 高度分档：{@code dy=0} 与 {@code dy=2} 记 2 分（各自都能单独钉死
	 * 目标，脚下一圈还不用管支撑），{@code dy=1} 记 0 分（目标站立时那一圈
	 * 通常被它自己的碰撞箱占着，服务器直接拒绝放置）。
	 *
	 * <p>
	 * 3. 距离：只作为同档的最终分先后，且乘上 {@code DISTANCE_WEIGHT=1e-4}
	 * 后永远小于 1，不会越档。
	 */
	private static double[] score(double[] distanceSq, double targetYaw)
	{
		double escapeX = escapeX(targetYaw);
		double escapeZ = escapeZ(targetYaw);

		double[] scores = new double[COUNT];
		for(int i = 0; i < COUNT; i++)
		{
			double distance = distanceSq == null ? 0 : distanceSq[i];
			if(!Double.isFinite(distance))
				distance = 0;

			scores[i] = ALIGN_WEIGHT * alignment(i, escapeX, escapeZ)
				+ tier(i) - DISTANCE_WEIGHT * distance;
		}

		return scores;
	}

	private static double alignment(int index, double escapeX, double escapeZ)
	{
		double length = Math.sqrt(escapeX * escapeX + escapeZ * escapeZ);
		if(length < 1.0E-4)
			return 0;

		return (OFFSET_X[index] * escapeX + OFFSET_Z[index] * escapeZ)
			/ length;
	}

	private static double escapeX(double targetYaw)
	{
		if(Double.isNaN(targetYaw))
			return 0;

		// 原版：yaw=0 朝 +Z，yaw=90 朝 -X
		return -Math.sin(Math.toRadians(targetYaw));
	}

	private static double escapeZ(double targetYaw)
	{
		if(Double.isNaN(targetYaw))
			return 0;

		return Math.cos(Math.toRadians(targetYaw));
	}

	private static int tier(int index)
	{
		int y = OFFSET_Y[index];
		return y == 1 ? 0 : TIER_HIGH;
	}

	private static final double ALIGN_WEIGHT = 4;
	private static final double DISTANCE_WEIGHT = 1.0E-4;
	private static final int TIER_HIGH = 2;
}
