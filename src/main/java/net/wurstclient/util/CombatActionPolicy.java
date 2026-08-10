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
