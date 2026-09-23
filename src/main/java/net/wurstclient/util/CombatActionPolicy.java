/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.function.IntPredicate;

public enum CombatActionPolicy
{
	;

	public static boolean isAttackMissCooldownActive(int missTime)
	{
		return missTime > 0 && missTime <= 10;
	}

	/** Hit Select 中判定"自己正在受伤、属于对拼"的 hurtTime 阈值（与 Epsilon 一致）。 */
	public static final int HIT_SELECT_TRADE_HURT_TIME = 6;

	/**
	 * Hit Select（移植自 Epsilon KillAura）：这一刀是否处在"能真正打中"的窗口。
	 *
	 * <p>
	 * 打不进窗口的刀伤害为 0，却照样会重置攻击者的攻击蓄力，等于白费一次满蓄力，
	 * 所以这种刀应当直接不发。
	 *
	 * <p>
	 * 判据一：目标已经走出无敌帧。用 ping 换算出的 tick 数补偿延迟，否则会在服务端
	 * 仍判定无敌时提前出刀。判据二：自己正处在受伤窗口里，说明在对拼，必须还手。
	 *
	 * @param targetHurtTime
	 *            目标的无敌帧剩余 tick，负数表示未知（放行）
	 * @param latencyTicks
	 *            到服务器的延迟，单位 tick，负数表示未知（放行）
	 * @param ownHurtTime
	 *            自己的受伤计时
	 */
	public static boolean isHitSelectWindowOpen(int targetHurtTime,
		int latencyTicks, int ownHurtTime)
	{
		if(targetHurtTime < 0 || latencyTicks < 0 || ownHurtTime < 0)
			return true;

		return targetHurtTime <= latencyTicks + 1
			|| ownHurtTime >= HIT_SELECT_TRADE_HURT_TIME;
	}

	/**
	 * 轮转攻击的游标推进：从 {@code current} 开始，在 {@code [0, size)} 里找第一个
	 * 满足 {@code eligible} 的下标并返回；找不到返回 {@code -1}。
	 *
	 * <p>
	 * 用于 MultiAura 的轮转攻击——一次 click 只打一个目标，下一轮从上一个的后一位
	 * 接着找。1.9+ 的攻击蓄力按攻击者计算，同一 tick 内只有第一刀是满蓄力，所以多目标
	 * 只能靠错开 tick 来达成满伤害。
	 *
	 * <p>
	 * 抽成纯函数是为了可单测：游标的取模与跳过逻辑最容易在列表增删时写错。
	 *
	 * @param current
	 *            当前游标，可为任意整数（内部按 size 取模）
	 * @param size
	 *            候选数量，{@code <= 0} 时返回 {@code -1}
	 * @param eligible
	 *            下标是否可攻击
	 */
	public static int findNextEligibleIndex(int current, int size,
		IntPredicate eligible)
	{
		if(size <= 0 || eligible == null)
			return -1;

		int start = Math.floorMod(current, size);
		for(int i = 0; i < size; i++)
		{
			int index = (start + i) % size;
			if(eligible.test(index))
				return index;
		}
		return -1;
	}

	public static boolean canCritical(CriticalState state,
		boolean ignoreOnGround, boolean allowSprinting)
	{
		return state != null && (ignoreOnGround || !state.onGround())
			&& !state.inFluid() && !state.onClimbable() && !state.passenger()
			&& !state.flying() && !state.fallFlying() && !state.noGravity()
			&& !state.handsBusy() && !state.blindness() && !state.levitation()
			&& !state.slowFalling() && state.attackStrength() > 0.9F
			&& (allowSprinting || !state.sprinting());
	}

	public static boolean canStartSpoofedCritical(CriticalState state,
		boolean requiresGround, boolean canStopSprinting)
	{
		return canCritical(state, true, canStopSprinting)
			&& (!requiresGround || state.onGround());
	}

	public record CriticalState(boolean onGround, boolean inFluid,
		boolean onClimbable, boolean passenger, boolean flying,
		boolean fallFlying, boolean noGravity, boolean handsBusy,
		boolean blindness, boolean levitation, boolean slowFalling,
		float attackStrength, boolean sprinting)
	{
	}
}
