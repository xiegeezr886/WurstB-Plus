package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ReachPolicyTest
{
	@Test
	void appliesConfiguredRangeWhenConditionsMatch()
	{
		assertEquals(4.2F, ReachPolicy.resolveEntityRange(4.2F, 3, true,
			true, true, false, true));
	}

	@Test
	void fallsBackWhenPlayerOrEnvironmentIsInvalid()
	{
		assertEquals(3, ReachPolicy.resolveEntityRange(4.2F, 3, false,
			true, false, false, false));
		assertEquals(3, ReachPolicy.resolveEntityRange(4.2F, 3, true,
			false, true, false, false));
		assertEquals(3, ReachPolicy.resolveEntityRange(4.2F, 3, true,
			true, false, true, true));
	}

	@Test
	void sanitizesNonFiniteRanges()
	{
		assertEquals(3, ReachPolicy.resolveEntityRange(Float.NaN, 3, true,
			true, false, false, false));
		assertEquals(3, ReachPolicy.resolveEntityRange(4, Float.NaN, false,
			true, false, false, false));
	}
}
