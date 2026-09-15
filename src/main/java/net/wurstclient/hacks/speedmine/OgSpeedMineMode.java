/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.speedmine;

import net.wurstclient.WurstClient;
import net.wurstclient.hacks.SpeedMineHack;

/**
 * OG：把"两次挖掘之间的间隔"压到 Cooldown 设定的 tick 数。
 *
 * <p>
 * 1.20.2 的 {@code MultiPlayerGameMode.destroyDelay} 是 public 字段，原版
 * {@code continueDestroyBlock()} 开头只要它 &gt; 0 就直接 return（这一 tick 不累积破坏进度），
 * 破坏成功后把它重置为 5。
 */
public final class OgSpeedMineMode implements SpeedMineMode
{
	@Override
	public String getName()
	{
		return "OG";
	}
	
	@Override
	public void onUpdate(SpeedMineHack hack)
	{
		WurstClient.MC.gameMode.destroyDelay = hack.getCooldown();
	}
}
