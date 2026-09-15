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
 * 环绕（Surround）四个水平候选位置的纯逻辑：决定谁先放、谁根本不放。
 *
 * <p>
 * 参考项目 OpenEpsilon 的 Surround 也是先把候选位置按方向收进一个 EnumMap，
 * 再按固定顺序取用——那边的顺序是有意的（正下方 → 北 → 东 → 南 → 西），
 * 因为在每 tick 只能放几个方块的限制下四面本来就补不完，先补哪一面决定了
 * 你还要暴露多久。本 hack 不补脚下，于是这里把“顺序”抽成纯函数：朝最近那个
 * 敌人的方向优先，没有敌人时保持原来的 东 → 西 → 南 → 北 顺序，行为与以前
 * 完全一致。
 *
 * <p>
 * 这个类不引用任何 Minecraft 类型，方便单元测试。
 */
public enum SurroundPlanner
{
	;

	public static final int COUNT = 4;

	// 与旧版 SurroundHack.SURROUND_POS 一一对应：东、西、南、北
	private static final int[] OFFSET_X = {1, -1, 0, 0};
	private static final int[] OFFSET_Z = {0, 0, 1, -1};

	public static int offsetX(int index)
	{
		return OFFSET_X[index];
	}

	public static int offsetZ(int index)
	{
		return OFFSET_Z[index];
	}

	/**
	 * 返回可以放置的方向索引，最危险的一侧排在最前面。
	 *
	 * @param playerX
	 *            玩家 X 坐标
	 * @param playerZ
	 *            玩家 Z 坐标
	 * @param threatX
	 *            最近敌人的 X 坐标，没有敌人时传 {@link Double#NaN}
	 * @param threatZ
	 *            最近敌人的 Z 坐标，没有敌人时传 {@link Double#NaN}
	 * @param usable
	 *            每个方向能否放置（可替换、没有被实体占住、不与玩家自己的
	 *            碰撞盒重叠），长度为 {@link #COUNT}
	 * @return 排好序、并且已经剔除不可放置方向的方向索引
	 */
	public static int[] plan(double playerX, double playerZ, double threatX,
		double threatZ, boolean[] usable)
	{
		double[] scores = score(playerX, playerZ, threatX, threatZ);

		int[] order = new int[COUNT];
		int size = 0;
		for(int i = 0; i < COUNT; i++)
			if(usable[i])
				order[size++] = i;

		// 插入排序：分数高的在前。分数相同时不交换，于是保持原来的
		// 东→西→南→北 顺序，结果才是确定的。没有敌人时敌人坐标是 NaN，
		// 所有分数都是 NaN，比较全部为 false，同样直接保持原顺序。
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
	 * 每个方向与“指向敌人的向量”的贴合程度：越正对敌人分数越高，背后的
	 * 方向分数最低。
	 */
	private static double[] score(double playerX, double playerZ, double threatX,
		double threatZ)
	{
		double dx = threatX - playerX;
		double dz = threatZ - playerZ;

		double[] scores = new double[COUNT];
		for(int i = 0; i < COUNT; i++)
			scores[i] = OFFSET_X[i] * dx + OFFSET_Z[i] * dz;

		return scores;
	}
}
