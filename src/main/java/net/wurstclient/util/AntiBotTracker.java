/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * AntiBot 的跨 tick 状态：把单 tick 的「贴地但竖直速度非零」升格为
 * 「连续成立」。
 *
 * <p>
 * 为什么需要它：原实现只用单 tick 观测判 bot，而客户端在插值、载具、被活塞
 * 推动、或者网络包丢了一个 onGround 位时，都可能让真人某一 tick 落进
 * 「onGround 为真 + deltaY 不为零」的状态，于是所有 combat hack 会瞬间停止
 * 攻击这个真人。真 bot（实体不支持或伪造竖直位移）则会让这个状态持续。
 *
 * <p>
 * 状态按 UUID 存，每 tick 用当轮真正还在线的 UUID 集合裁剪，玩家下线 / 卸载
 * 后条目自动消失，不需要额外的死亡或断线钩子，也不会泄漏。
 */
public final class AntiBotTracker
{
	/** 连续多少个 tick 成立才认定，1 表示只需 1 tick（等同原行为）。 */
	private final int graceTicks;

	private final Map<UUID, Integer> streaks = new HashMap<>();

	public AntiBotTracker(int graceTicks)
	{
		this.graceTicks = Math.max(1, graceTicks);
	}

	/**
	 * 登记本轮观测，返回「连续成立是否已达阈值」。{@code graceTicks} 为 2 时
	 * 第 2 个连续 tick 就会返回 true。
	 */
	public boolean noteImpossibleGround(UUID uuid, boolean now)
	{
		if(uuid == null)
			return false;

		if(!now)
		{
			streaks.remove(uuid);
			return false;
		}

		int streak = streaks.merge(uuid, 1, Integer::sum);
		return streak >= graceTicks;
	}

	/**
	 * 丢弃本轮不在线的 UUID。每 tick 在遍历完实体后调用一次。
	 */
	public void retainOnly(Set<UUID> online)
	{
		streaks.keySet().retainAll(online);
	}

	public void reset()
	{
		streaks.clear();
	}

	public int trackedCount()
	{
		return streaks.size();
	}

	public Set<UUID> trackedUuids()
	{
		return new HashSet<>(streaks.keySet());
	}
}
