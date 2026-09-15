/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.speedmine;

import net.wurstclient.hacks.SpeedMineHack;

/**
 * SpeedMine 的一种实现方式（Haste / OG）。
 *
 * <p>
 * Haste 模式的一次性状态（是否是我们加的急迫、原本的急迫是什么）搬进了
 * {@link HasteSpeedMineMode}，所以 {@link #onDisable()} 由模式自己实现"收拾干净"。
 */
public interface SpeedMineMode
{
	/** 显示在 Mode 设置里的名字。 */
	String getName();
	
	/** 每个 tick 执行一次。 */
	void onUpdate(SpeedMineHack hack);
	
	/** 关闭 SpeedMine 时清理本模式留下的东西。 */
	default void onDisable()
	{}
}
