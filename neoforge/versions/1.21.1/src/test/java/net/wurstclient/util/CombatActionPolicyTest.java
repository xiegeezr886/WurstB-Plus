package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.wurstclient.util.CombatActionPolicy.CriticalState;
import org.junit.jupiter.api.Test;

final class CombatActionPolicyTest
{
	@Test
	void distinguishesAttackCooldownFromOpenScreenSentinel()
	{
		assertFalse(CombatActionPolicy.isAttackMissCooldownActive(0));
		assertTrue(CombatActionPolicy.isAttackMissCooldownActive(1));
		assertTrue(CombatActionPolicy.isAttackMissCooldownActive(10));
		assertFalse(CombatActionPolicy.isAttackMissCooldownActive(11));
		assertFalse(CombatActionPolicy.isAttackMissCooldownActive(10000));
	}

	@Test
	void acceptsReadyPacketCriticalState()
	{
		CriticalState state = new CriticalState(true, false, false, false,
			false, false, false, false, false, false, false, 1, false);
		assertTrue(CombatActionPolicy.canCritical(state, true, false));
	}

	@Test
	void rejectsBlockedOrUnchargedCriticalState()
	{
		CriticalState fluid = new CriticalState(true, true, false, false,
			false, false, false, false, false, false, false, 1, false);
		CriticalState uncharged = new CriticalState(true, false, false, false,
			false, false, false, false, false, false, false, 0.5F, false);
		assertFalse(CombatActionPolicy.canCritical(fluid, true, false));
		assertFalse(CombatActionPolicy.canCritical(uncharged, true, false));
	}

	@Test
	void rejectsSprintingWhenItCannotBeStopped()
	{
		CriticalState sprinting = new CriticalState(true, false, false, false,
			false, false, false, false, false, false, false, 1, true);

		assertFalse(CombatActionPolicy.canCritical(sprinting, true, false));
		assertTrue(CombatActionPolicy.canCritical(sprinting, true, true));
	}
}
