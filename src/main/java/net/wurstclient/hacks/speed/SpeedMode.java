/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.speed;

import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.hacks.SpeedHackHack;

/**
 * Speed 的一种实现方式（NCP Bhop / Strafe / LowHop / OnGround / Brutal）。
 *
 * <p>
 * 参考 OpenOpal 的模块-模式结构。hack 负责通用流程（能不能控制、有没有在移动、
 * 目标速度怎么算），模式只负责"这一 tick 怎么把速度写到玩家身上"。
 * 模式自己的状态（LowHop 的 {@code lowHopActive}）放在模式类里，通过
 * {@link #reset()} 由 hack 在失去控制/不移动/切换模式时清掉。
 */
public interface SpeedMode
{
	/** 显示在 Mode 设置里的名字。 */
	String getName();
	
	/**
	 * 应用这一 tick 的速度。
	 *
	 * @param targetSpeed
	 *            已经算好的目标速度（BASE_SPEED × Speed 设置 × 速度药水倍率）
	 */
	void apply(SpeedHackHack hack, LocalPlayer player, float forward,
		float sideways, double targetSpeed);
	
	/** 清掉本模式的状态。 */
	default void reset()
	{}
}
