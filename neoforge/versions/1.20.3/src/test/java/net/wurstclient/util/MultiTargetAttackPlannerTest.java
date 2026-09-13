package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class MultiTargetAttackPlannerTest
{
	@Test
	void preservesOrderWhileFilteringInvalidAndInvulnerableTargets()
	{
		List<String> candidates = List.of("first", "invalid", "hurt", "last");
		Map<String, Integer> hurtTimes =
			Map.of("first", 0, "invalid", 0, "hurt", 8, "last", 3);

		assertEquals(List.of("first", "last"), MultiTargetAttackPlanner.plan(
			candidates, value -> !value.equals("invalid"), hurtTimes::get, 5,
			10));
	}

	@Test
	void appliesTargetLimitAfterValidation()
	{
		assertEquals(List.of(2, 4), MultiTargetAttackPlanner.plan(
			List.of(1, 2, 3, 4, 6), value -> value % 2 == 0, value -> 0, 10,
			2));
	}

	@Test
	void rejectsEmptyOrDisabledPlans()
	{
		assertTrue(MultiTargetAttackPlanner.plan(List.of(), value -> true,
			value -> 0, 10, 5).isEmpty());
		assertTrue(MultiTargetAttackPlanner.plan(List.of(1), value -> true,
			value -> 0, 10, -1).isEmpty());
	}

	@Test
	void zeroTargetLimitMeansUnlimited()
	{
		assertEquals(List.of(1, 2, 3), MultiTargetAttackPlanner.plan(
			List.of(1, 2, 3), value -> true, value -> 0, 10, 0));
	}
}
