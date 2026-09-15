/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.step;

import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.hacks.StepHack;

/**
 * Step 的一种实现方式（Simple / Legit）。
 *
 * <p>
 * 通用部分（玩家/世界检查、追踪 maxUpStep 原值、冷却递减）留在 hack 里，
 * 模式只负责"这一 tick 怎么抬上去"。
 */
public interface StepMode
{
	/** 显示在 Mode 设置里的名字。 */
	String getName();
	
	/**
	 * 每个 tick 调用一次（玩家非空、已 track、冷却已递减之后）。
	 *
	 * @param player
	 *            当前玩家（与 {@code WurstClient.MC.player} 相同）
	 */
	void onUpdate(StepHack hack, LocalPlayer player);
}
