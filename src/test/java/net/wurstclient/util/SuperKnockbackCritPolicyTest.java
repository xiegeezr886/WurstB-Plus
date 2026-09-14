package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.util.SuperKnockbackCritPolicy.State;

final class SuperKnockbackCritPolicyTest
{
	@Test
	void acceptsAirborneChargedAttackThatVanillaWouldCrit()
	{
		assertTrue(SuperKnockbackCritPolicy.isNaturalCritical(state()));
	}

	/**
	 * 回归用例：疾跑时原版不可能暴击（{@code Player#attack} 里
	 * {@code flag2 = flag2 && !isSprinting()}），而疾跑跳跃下落恰好满足
	 * 「离地 + 在下降」那一半，所以旧实现会把它误判成暴击、并因此跳过疾跑重置。
	 */
	@Test
	void rejectsSprintJumpAttack()
	{
		assertFalse(SuperKnockbackCritPolicy
			.isNaturalCritical(state(false, false, false, true, false, false,
				0.5F, 1)));
	}

	@Test
	void requiresFullyChargedAttack()
	{
		assertFalse(SuperKnockbackCritPolicy
			.isNaturalCritical(state(false, false, false, false, false, false,
				0.5F, 0.9F)));
		assertTrue(SuperKnockbackCritPolicy
			.isNaturalCritical(state(false, false, false, false, false, false,
				0.5F, 0.91F)));
	}

	@Test
	void rejectsGroundedClimbableOrWetAttack()
	{
		assertFalse(SuperKnockbackCritPolicy
			.isNaturalCritical(state(true, false, false, false, false, false,
				0.5F, 1)));
		assertFalse(SuperKnockbackCritPolicy
			.isNaturalCritical(state(false, true, false, false, false, false,
				0.5F, 1)));
		assertFalse(SuperKnockbackCritPolicy
			.isNaturalCritical(state(false, false, true, false, false, false,
				0.5F, 1)));
	}

	@Test
	void rejectsPassengerBlindnessAndZeroFallDistance()
	{
		assertFalse(SuperKnockbackCritPolicy
			.isNaturalCritical(state(false, false, false, false, true, false,
				0.5F, 1)));
		assertFalse(SuperKnockbackCritPolicy
			.isNaturalCritical(state(false, false, false, false, false, true,
				0.5F, 1)));
		assertFalse(SuperKnockbackCritPolicy
			.isNaturalCritical(state(false, false, false, false, false, false,
				0.0F, 1)));
	}

	@Test
	void rejectsNullState()
	{
		assertFalse(SuperKnockbackCritPolicy.isNaturalCritical(null));
	}

	/**
	 * 一次原版会判为暴击的攻击：满充能、离地在下降、不在水里、没骑乘、没失明、
	 * 也没在疾跑。
	 */
	private static State state()
	{
		return state(false, false, false, false, false, false, 0.5F, 1);
	}

	private static State state(boolean onGround, boolean onClimbable,
		boolean inWater, boolean sprinting, boolean passenger,
		boolean blindness, float fallDistance, float attackStrength)
	{
		return new State(onGround, onClimbable, inWater, sprinting, passenger,
			blindness, fallDistance, attackStrength);
	}
}
