/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.criticals;

import net.wurstclient.hacks.CriticalsHack;

/**
 * Criticals 的一种实现方式（Packet / NoGround / Mini jump / Jump）。
 *
 * <p>
 * 参考 OpenOpal 的 {@code criticals/impl/*} 结构：每种模式一个类，模式内部的细节
 * （发什么包、要不要跳跃）不写在 hack 的 switch 里，新增模式也不用改 hack。hack 只负责
 * 通用流程：状态检查 → 停冲刺 → 交给模式 → 粒子。
 */
public interface CriticalsMode
{
	/** 显示在 Mode 设置里的名字。 */
	String getName();
	
	/**
	 * 这个模式要玩家站在地上才有意义吗（Mini jump / Jump 为 true）。
	 * 用于 {@code CombatActionPolicy.canStartSpoofedCritical()} 的前置检查。
	 */
	default boolean requiresGround()
	{
		return false;
	}
	
	/**
	 * 玩家攻击到实体后执行本模式的动作。
	 *
	 * @return false 表示"这次不打暴击"（例如需要在但不在、或不在半空中的情况），
	 *         调用方会跳过后面的粒子等动作。
	 */
	boolean doCriticals(CriticalsHack hack);
}
