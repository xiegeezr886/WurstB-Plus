/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

public enum KnockbackBoostPolicy
{
	;

	public static boolean shouldBoost(State state, int maximumHurtTime,
		int chancePercent, int chanceRoll)
	{
		if(state == null || !state.livingTarget() || state.criticalAttack()
			|| state.hurtTime() < 0 || state.hurtTime() > maximumHurtTime)
			return false;
		if(state.onlyMoving() && !state.moving())
			return false;
		if(state.onlyForward() && state.movingSideways())
			return false;
		if(state.onlyGround() && !state.onGround())
			return false;
		if(state.rejectFluids() && state.inFluid())
			return false;
		if(state.requireSprint() && !state.sprinting())
			return false;

		int safeChance = Math.max(0, Math.min(100, chancePercent));
		int safeRoll = Math.max(0, Math.min(99, chanceRoll));
		return safeRoll < safeChance;
	}

	public record State(boolean livingTarget, int hurtTime, boolean moving,
		boolean movingSideways, boolean onGround, boolean inFluid,
		boolean sprinting, boolean criticalAttack, boolean onlyMoving,
		boolean onlyForward, boolean onlyGround, boolean rejectFluids,
		boolean requireSprint)
	{
	}
}
