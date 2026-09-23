/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

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
