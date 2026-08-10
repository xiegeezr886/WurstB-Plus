package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class RotationCorrectionPolicyTest
{
	@Test
	void keepsAbsoluteRotationAndNeutralizesRelativeRotation()
	{
		assertEquals(73.5F,
			RotationCorrectionPolicy.packetRotation(73.5F, false));
		assertEquals(0,
			RotationCorrectionPolicy.packetRotation(73.5F, true));
	}

	@Test
	void sanitizesNonFiniteRotation()
	{
		assertEquals(0,
			RotationCorrectionPolicy.packetRotation(Float.NaN, false));
		assertEquals(0,
			RotationCorrectionPolicy.packetRotation(Float.POSITIVE_INFINITY, true));
	}
}
