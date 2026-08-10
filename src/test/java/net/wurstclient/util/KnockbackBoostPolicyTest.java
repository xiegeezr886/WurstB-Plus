package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.util.KnockbackBoostPolicy.State;

final class KnockbackBoostPolicyTest
{
	@Test
	void acceptsEligibleAttackWithinChanceAndHurtTime()
	{
		assertTrue(KnockbackBoostPolicy.shouldBoost(state(), 10, 50, 49));
		assertFalse(KnockbackBoostPolicy.shouldBoost(state(), 10, 50, 50));
		assertFalse(KnockbackBoostPolicy.shouldBoost(state(11, true, false,
			true, false, true, false), 10, 100, 0));
	}

	@Test
	void appliesMovementEnvironmentAndCriticalFilters()
	{
		assertFalse(KnockbackBoostPolicy.shouldBoost(state(0, false, false,
			true, false, true, false), 10, 100, 0));
		assertFalse(KnockbackBoostPolicy.shouldBoost(state(0, true, true,
			true, false, true, false), 10, 100, 0));
		assertFalse(KnockbackBoostPolicy.shouldBoost(state(0, true, false,
			false, false, true, false), 10, 100, 0));
		assertFalse(KnockbackBoostPolicy.shouldBoost(state(0, true, false,
			true, true, true, false), 10, 100, 0));
		assertFalse(KnockbackBoostPolicy.shouldBoost(state(0, true, false,
			true, false, false, false), 10, 100, 0));
		assertFalse(KnockbackBoostPolicy.shouldBoost(state(0, true, false,
			true, false, true, true), 10, 100, 0));
	}

	@Test
	void clampsChanceAndRejectsInvalidTargets()
	{
		assertTrue(KnockbackBoostPolicy.shouldBoost(state(), 10, 150, 99));
		assertFalse(KnockbackBoostPolicy.shouldBoost(state(), 10, -1, 0));
		assertFalse(KnockbackBoostPolicy.shouldBoost(null, 10, 100, 0));
	}

	private static State state()
	{
		return state(0, true, false, true, false, true, false);
	}

	private static State state(int hurtTime, boolean moving, boolean sideways,
		boolean onGround, boolean inFluid, boolean sprinting, boolean critical)
	{
		return new State(true, hurtTime, moving, sideways, onGround, inFluid,
			sprinting, critical, true, true, true, true, true);
	}
}
