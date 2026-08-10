package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RotationRequestPolicyTest
{
	@Test
	void directRequestExpiresAfterItsLease()
	{
		assertTrue(RotationRequestPolicy.isFresh(10, 10, 1));
		assertTrue(RotationRequestPolicy.isFresh(10, 11, 1));
		assertFalse(RotationRequestPolicy.isFresh(10, 12, 1));
	}

	@Test
	void rejectsMissingOrOutOfOrderRequests()
	{
		assertFalse(RotationRequestPolicy.isFresh(-1, 10, 1));
		assertFalse(RotationRequestPolicy.isFresh(11, 10, 1));
	}
}
