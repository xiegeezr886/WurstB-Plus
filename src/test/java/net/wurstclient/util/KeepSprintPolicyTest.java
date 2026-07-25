package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class KeepSprintPolicyTest
{
	@Test
	void keepSprintPreservesFullHorizontalMotion()
	{
		assertEquals(1, KeepSprintPolicy.attackMotionMultiplier(0.6, true));
		assertEquals(0.6,
			KeepSprintPolicy.attackMotionMultiplier(0.6, false));
	}

	@Test
	void keepSprintOnlyBlocksSprintCancellation()
	{
		assertFalse(KeepSprintPolicy.shouldApplySprintChange(true, false));
		assertTrue(KeepSprintPolicy.shouldApplySprintChange(true, true));
		assertTrue(KeepSprintPolicy.shouldApplySprintChange(false, false));
	}
}
