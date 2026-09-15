/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.Locale;
import java.util.Map;

/**
 * AntiBot 的纯判定逻辑：输入是「某个玩家这一 tick 的观测快照」，输出是
 * {@link Verdict}。不引用任何 Minecraft 类型，便于单测。
 *
 * <p>
 * 快照里的 {@code impossibleGroundNow} 由 {@link AntiBotTracker} 维护成
 * 「连续成立若干 tick」的结论，本类只消费结果——原实现直接用
 * {@code onGround() && |deltaY| > 0.1} 判一次，会把客户端插值/载具里的真人
 * 误判成 bot（见 AntiBot.md 的对照证据）。
 */
public enum AntiBotPredicate
{
	;

	/**
	 * 判定结果。
	 *
	 * <p>
	 * 这里**没有**「太年轻」这一档：`AntiBotHack` 的 `isBot()` 只读
	 * `detectedBots` 集合，而 `EntityUtils.IS_ATTACKABLE` 又只问 `isBot()`，
	 * 所以「最小年龄」这一条只要不返回 {@link #BOT}，刚进服的真人就会立刻被
	 * 所有 combat hack 攻击——这与设置项说明「暂时忽略」正好相反。曾经拆出过
	 * 一个 {@code TOO_YOUNG} 枚举值，经复核是行为回退，已删掉。
	 */
	public enum Verdict
	{
		/** 命中某条 bot 判据。 */
		BOT,

		/** 没有命中任何判据。 */
		HUMAN
	}

	/**
	 * 单个玩家在一个 tick 上的观测值。所有 Minecraft 侧取值都在
	 * {@code AntiBotHack} 里完成，这里只保留原始数值。
	 *
	 * @param normalizedName
	 *            小写化后的玩家档案名，用于重名判据
	 * @param hasPlayerInfo
	 *            该 UUID 在 tab 列表里是否有对应条目
	 * @param hasGameMode
	 *            tab 条目是否带游戏模式（快照里没有条目时为 false）
	 * @param latency
	 *            tab 条目的延迟；没有条目时为 -1
	 * @param impossibleGround
	 *            本 tick 是否满足「贴地但竖直速度非零」
	 * @param invisible
	 *            是否处于隐身状态
	 * @param pitch
	 *            俯仰角
	 * @param health
	 *            当前生命值
	 * @param maxHealth
	 *            最大生命值
	 * @param entityId
	 *            实体数字 ID
	 * @param uuid
	 *            UUID 字符串形式
	 * @param tickCount
	 *            该实体已经存在的 tick 数
	 */
	public record Snapshot(String normalizedName, boolean hasPlayerInfo,
		boolean hasGameMode, int latency, boolean impossibleGround,
		boolean invisible, float pitch, float health, float maxHealth,
		int entityId, String uuid, int tickCount)
	{
	}

	/**
	 * @param nameCounts
	 *            本轮世界里「小写化档案名 → 出现次数」
	 */
	public static Verdict classify(Snapshot s, Map<String, Integer> nameCounts,
		AntiBotSettings settings)
	{
		if(settings.checkPlayerInfo() && !s.hasPlayerInfo())
			return Verdict.BOT;
		if(settings.checkGameMode() && s.hasPlayerInfo() && !s.hasGameMode())
			return Verdict.BOT;
		if(settings.checkPing() && s.hasPlayerInfo() && s.latency() <= 0)
			return Verdict.BOT;
		if(settings.checkGround() && s.impossibleGround())
			return Verdict.BOT;
		if(settings.checkInvisible() && s.invisible())
			return Verdict.BOT;
		if(settings.checkIllegalPitch() && Math.abs(s.pitch()) > 90)
			return Verdict.BOT;
		if(settings.checkIllegalHealth()
			&& (!Float.isFinite(s.health()) || s.health() < 0
				|| s.health() > s.maxHealth()))
			return Verdict.BOT;
		if(settings.checkEntityId()
			&& (s.entityId() < 0 || s.entityId() > 1_000_000_000))
			return Verdict.BOT;
		if(settings.checkDuplicateName()
			&& nameCounts.getOrDefault(s.normalizedName(), 0) > 1)
			return Verdict.BOT;
		if(s.tickCount() < settings.minimumAgeTicks())
			return Verdict.BOT;

		if(!settings.checkUuid())
			return Verdict.HUMAN;
		return isSuspiciousUuid(s.uuid()) ? Verdict.BOT : Verdict.HUMAN;
	}

	/**
	 * 参考实现的 UUID 判据只有「全零 / 尾部全零」两条模式，这里保持一致。
	 */
	public static boolean isSuspiciousUuid(String uuid)
	{
		return uuid.startsWith("00000000") || uuid.endsWith("000000000000");
	}

	/**
	 * 「贴地但竖直速度非零」——单 tick 观测量。真正的判据是它在
	 * {@link AntiBotTracker} 里连续成立若干 tick。
	 */
	public static boolean isImpossibleGroundState(boolean onGround,
		double deltaY)
	{
		return onGround && Math.abs(deltaY) > 0.1;
	}

	/**
	 * 小写化档案名，用于重名判据。
	 */
	public static String normalizeName(String gameProfileName)
	{
		return gameProfileName.toLowerCase(Locale.ROOT);
	}
}
