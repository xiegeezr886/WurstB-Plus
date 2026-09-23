package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

	@Test
	void groundedModesRejectAirborneStateBeforeSideEffects()
	{
		CriticalState grounded = new CriticalState(true, false, false, false,
			false, false, false, false, false, false, false, 1, true);
		CriticalState airborne = new CriticalState(false, false, false, false,
			false, false, false, false, false, false, false, 1, true);

		assertTrue(CombatActionPolicy.canStartSpoofedCritical(grounded, true,
			true));
		assertFalse(CombatActionPolicy.canStartSpoofedCritical(airborne, true,
			true));
		assertTrue(CombatActionPolicy.canStartSpoofedCritical(airborne, false,
			true));
	}

	@Test
	void hitSelectWaitsForTargetInvulnerabilityToExpire()
	{
		// 无延迟时，目标 hurtTime 必须降到 1 以内才出刀
		assertTrue(CombatActionPolicy.isHitSelectWindowOpen(0, 0, 0));
		assertTrue(CombatActionPolicy.isHitSelectWindowOpen(1, 0, 0));
		assertFalse(CombatActionPolicy.isHitSelectWindowOpen(2, 0, 0));
		assertFalse(CombatActionPolicy.isHitSelectWindowOpen(10, 0, 0));
	}

	@Test
	void hitSelectCompensatesLatencyInTicks()
	{
		// 100ms 延迟 = 2 tick，窗口随之放宽到 hurtTime <= 3
		assertTrue(CombatActionPolicy.isHitSelectWindowOpen(3, 2, 0));
		assertFalse(CombatActionPolicy.isHitSelectWindowOpen(4, 2, 0));
	}

	@Test
	void hitSelectAllowsTradingWhileTakingDamage()
	{
		// 自己正在受伤（>= 6）说明在对拼，即使目标仍在无敌帧也还手
		assertTrue(CombatActionPolicy.isHitSelectWindowOpen(10, 0,
			CombatActionPolicy.HIT_SELECT_TRADE_HURT_TIME));
		assertTrue(CombatActionPolicy.isHitSelectWindowOpen(10, 0, 10));
		assertFalse(CombatActionPolicy.isHitSelectWindowOpen(10, 0, 5));
	}

	@Test
	void hitSelectPassesWhenHurtTimeIsUnknown()
	{
		// 负数表示取不到值，此时放行，不能因为它把攻击整个卡死
		assertTrue(CombatActionPolicy.isHitSelectWindowOpen(-1, 0, 0));
		assertTrue(CombatActionPolicy.isHitSelectWindowOpen(10, -1, 0));
		assertTrue(CombatActionPolicy.isHitSelectWindowOpen(10, 0, -1));
	}

	@Test
	void roundRobinVisitsEveryTargetInOrder()
	{
		int size = 3;
		int cursor = 0;
		for(int round = 0; round < 2; round++)
			for(int expected = 0; expected < size; expected++)
			{
				int index = CombatActionPolicy.findNextEligibleIndex(cursor, size,
					i -> true);
				assertEquals(expected, index);
				cursor = index + 1;
			}
	}

	@Test
	void roundRobinSkipsIneligibleTargets()
	{
		// 下标 1 不可攻击 -> 0 之后应直接跳到 2
		int index = CombatActionPolicy.findNextEligibleIndex(1, 3, i -> i != 1);
		assertEquals(2, index);
	}

	@Test
	void roundRobinWrapsAndToleratesStaleCursor()
	{
		// 游标超出当前列表长度（目标死亡后列表变短）时要能回绕，不能越界。
		// 注意这里是取模而不是截断：7 % 3 = 1、13 % 3 = 1、floorMod(-2, 3) = 1。
		assertEquals(1, CombatActionPolicy.findNextEligibleIndex(7, 3, i -> true));
		assertEquals(1, CombatActionPolicy.findNextEligibleIndex(13, 3, i -> true));
		assertEquals(1, CombatActionPolicy.findNextEligibleIndex(-2, 3, i -> true));
		assertEquals(0, CombatActionPolicy.findNextEligibleIndex(3, 3, i -> true));
	}

	@Test
	void roundRobinReturnsMinusOneWhenNothingIsEligible()
	{
		assertEquals(-1, CombatActionPolicy.findNextEligibleIndex(0, 3, i -> false));
		assertEquals(-1, CombatActionPolicy.findNextEligibleIndex(0, 0, i -> true));
		assertEquals(-1, CombatActionPolicy.findNextEligibleIndex(0, -5, i -> true));
		assertEquals(-1, CombatActionPolicy.findNextEligibleIndex(0, 3, null));
	}
}
