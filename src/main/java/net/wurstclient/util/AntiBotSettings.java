/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

/**
 * AntiBot 的开关集合，纯数据，不引用任何 Minecraft 类型，便于单测。
 *
 * <p>
 * 字段顺序与 {@code AntiBotHack.isBot(Player, PlayerInfo, Map)} 里的检查顺序
 * 一一对应，改动顺序会改变判定结果（先命中的判据优先），因此改动这里必须同步
 * 更新单测。
 */
public record AntiBotSettings(
	boolean checkPlayerInfo,
	boolean checkGameMode,
	boolean checkPing,
	boolean checkGround,
	boolean checkInvisible,
	boolean checkUuid,
	boolean checkIllegalPitch,
	boolean checkIllegalHealth,
	boolean checkEntityId,
	boolean checkDuplicateName,
	int minimumAgeTicks)
{
}
