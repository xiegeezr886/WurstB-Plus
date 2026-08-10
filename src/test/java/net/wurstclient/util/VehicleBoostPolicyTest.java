package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

final class VehicleBoostPolicyTest
{
	@Test
	void pointsVelocityAlongPlayerYaw()
	{
		assertVec(new Vec3(0, 1, 2),
			VehicleBoostPolicy.velocity(0, 2, 1));
		assertVec(new Vec3(-2, 1, 0),
			VehicleBoostPolicy.velocity(90, 2, 1));
		assertVec(new Vec3(0, 1, -2),
			VehicleBoostPolicy.velocity(180, 2, 1));
	}

	@Test
	void sanitizesInvalidAndNegativeInputs()
	{
		assertVec(Vec3.ZERO,
			VehicleBoostPolicy.velocity(Float.NaN, -2, Double.NaN));
	}

	private static void assertVec(Vec3 expected, Vec3 actual)
	{
		assertEquals(expected.x, actual.x, 1.0E-9);
		assertEquals(expected.y, actual.y, 1.0E-9);
		assertEquals(expected.z, actual.z, 1.0E-9);
	}
}
