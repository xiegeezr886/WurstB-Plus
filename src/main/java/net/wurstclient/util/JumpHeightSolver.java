/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

/**
 * HighJump 的换算：把「Height（格）」变成原版跳跃所需的初速。
 *
 * <p>
 * 为什么需要它：{@code HighJumpHack} 原实现是 {@code height * 0.1} 直接加在原版
 * 跳跃力上，隐含假设是「无阻力抛物线 {@code h = v² / (2g)}」。但 1.20.1 的竖直
 * 运动不是这条公式：{@code LivingEntity#travel} 每 tick 先用当前速度位移，然后
 * {@code vy = (vy - 0.08) * 0.98}（反编译源里就是 {@code d0 = 0.08D} 与
 * {@code d2 * 0.98F}），阻力让两者偏离很大。例：
 *
 * <ul>
 * <li>Height = 6（默认值）：旧实现 v0 = 0.42 + 0.6 = 1.02，真实最高点 6.135 格；</li>
 * <li>Height = 20：旧实现 v0 = 2.42，真实最高点 27.718 格；</li>
 * <li>Height = 100：旧实现 v0 = 10.42，真实最高点 269.352 格（是设定值的 2.7 倍）。</li>
 * </ul>
 *
 * 本类按原版的逐 tick 积分反解出「正好到 N 格」所需的 v0，因此把 Height 变成
 * 真实高度。纯数学，不引用任何 Minecraft 类型。
 */
public enum JumpHeightSolver
{
	;

	/** 1.20.1 原版每 tick 的竖直重力（{@code LivingEntity#travel} 的 {@code d0}）。 */
	public static final double GRAVITY = 0.08;

	/** 1.20.1 原版竖直速度的每 tick 衰减（{@code travel} 里的 {@code 0.98F}）。 */
	public static final double DRAG = 0.98;

	/** 安全上限：v0 = 10000 时约需 2.5 万 tick 才落回，远超任何滑块取值。 */
	private static final int MAX_TICKS = 100000;

	/** 二分次数：80 次足以把 0..2^n 的区间压到 double 精度。 */
	private static final int SEARCH_STEPS = 80;

	/**
	 * 以竖直初速 {@code initialVelocity} 起跳，按原版积分能到达的最高格数。
	 *
	 * <p>
	 * 逐 tick 累加正的速度，遇到非正速度停止——原版竖直速度单调递减，所以
	 * 累计高度的最大值就是「所有正的位移之和」。
	 */
	public static double apexHeight(double initialVelocity)
	{
		double height = 0;
		double velocity = initialVelocity;

		for(int tick = 0; tick < MAX_TICKS && velocity > 0; tick++)
		{
			height += velocity;
			velocity = (velocity - GRAVITY) * DRAG;
		}

		return height;
	}

	/**
	 * {@link #apexHeight} 的反函数：正好跳到 {@code height} 格所需的竖直初速。
	 */
	public static double requiredVelocity(double height)
	{
		if(!(height > 0))
			return 0;

		// 先找一个一定够高的上界，再二分
		double high = Math.max(1, Math.sqrt(height * 2 * GRAVITY) + 1);
		while(apexHeight(high) < height && high < 1.0E6)
			high *= 2;

		double low = 0;
		for(int i = 0; i < SEARCH_STEPS; i++)
		{
			double mid = (low + high) / 2;
			if(apexHeight(mid) < height)
				low = mid;
			else
				high = mid;
		}

		return high;
	}
}
