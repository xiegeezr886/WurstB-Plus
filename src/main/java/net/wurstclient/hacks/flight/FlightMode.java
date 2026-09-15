/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.flight;

import net.minecraft.world.phys.Vec3;

/**
 * Flight 的一种实现方式（Vanilla / Boost / Rocket）。
 *
 * <p>
 * 参考 OpenOpal 的模块-模式结构，但 Flight 的模式不是"对事件做什么"，而是
 * "算出这一 tick 的水平移动 + 一个垂直倍率"，所以接口只有这两件事。
 * 模式不需要 hack 引用，参数由调用方给全。
 */
public interface FlightMode
{
	/** 显示在 Mode 设置里的名字。 */
	String getName();
	
	/**
	 * 算出这一 tick 的水平移动向量。
	 *
	 * @param delta
	 *            当前速度
	 * @param forward
	 *            前后输入（{@code input.forwardImpulse}）
	 * @param sideways
	 *            左右输入（{@code input.leftImpulse}）
	 * @param yRot
	 *            玩家朝向
	 * @param horizontal
	 *            设置里的水平速度（已应用 Slow sneaking）
	 */
	Vec3 getHorizontalMovement(Vec3 delta, float forward, float sideways,
		float yRot, double horizontal);
	
	/** 垂直速度要乘的倍数（Rocket 为 3，其余为 1）。 */
	default double getVerticalMultiplier()
	{
		return 1;
	}
}
