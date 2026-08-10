package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class MovementPlannerTest
{
	@Test
	void convertsInputToNormalizedWorldMotion()
	{
		Vec3 forward = MovementPlanner.horizontalMotion(1, 0, 0, 0.5);
		Vec3 diagonal = MovementPlanner.horizontalMotion(1, 1, 0, 0.5);

		assertEquals(0, forward.x, 1.0E-9);
		assertEquals(0.5, forward.z, 1.0E-9);
		assertEquals(0.5, Math.hypot(diagonal.x, diagonal.z), 1.0E-9);
	}

	@Test
	void blendsAndClampsWithoutChangingVerticalMotion()
	{
		Vec3 blended = MovementPlanner.blendHorizontal(new Vec3(1, -0.2, 0),
			1, 0, 0, 0.4, 0.5);
		Vec3 clamped = MovementPlanner.clampHorizontal(blended, 0.5);

		assertEquals(-0.2, clamped.y, 1.0E-9);
		assertTrue(Math.hypot(clamped.x, clamped.z) <= 0.5000001);
	}

	@Test
	void rotatesCardinalInputWithPlayerYaw()
	{
		Vec3 west = MovementPlanner.horizontalMotion(1, 0, 90, 1);
		Vec3 north = MovementPlanner.horizontalMotion(1, 0, 180, 1);

		assertEquals(-1, west.x, 1.0E-9);
		assertEquals(0, west.z, 1.0E-9);
		assertEquals(0, north.x, 1.0E-9);
		assertEquals(-1, north.z, 1.0E-9);
	}

	@Test
	void handlesEmptyInputAndNonPositiveLimits()
	{
		assertEquals(Vec3.ZERO,
			MovementPlanner.horizontalMotion(0, 0, 45, 1));

		Vec3 clamped = MovementPlanner.clampHorizontal(new Vec3(2, -0.3, 1), 0);
		assertEquals(0, clamped.x, 1.0E-9);
		assertEquals(-0.3, clamped.y, 1.0E-9);
		assertEquals(0, clamped.z, 1.0E-9);
	}

	@Test
	void preservesExternalHorizontalImpulse()
	{
		Vec3 current = new Vec3(1.2, 0.1, -0.4);
		Vec3 proposed = new Vec3(0.3, 0.42, 0);
		Vec3 result = MovementPlanner.clampControlledHorizontal(current,
			proposed, 0.5);

		assertEquals(current.x, result.x, 1.0E-9);
		assertEquals(current.z, result.z, 1.0E-9);
		assertEquals(proposed.y, result.y, 1.0E-9);
	}

	@Test
	void rejectsNonFiniteControlInput()
	{
		assertFalse(MovementPlanner.isMoving(Float.NaN, 1));
		assertEquals(Vec3.ZERO,
			MovementPlanner.horizontalMotion(1, 0, Float.NaN, 1));

		Vec3 current = new Vec3(0.2, -0.1, 0);
		assertEquals(current, MovementPlanner.blendHorizontal(current, 1, 0, 0,
			1, Double.NaN));
		Vec3 clamped = MovementPlanner.clampHorizontal(current, Double.NaN);
		assertEquals(0, clamped.x, 1.0E-9);
		assertEquals(-0.1, clamped.y, 1.0E-9);

		Vec3 recovered = MovementPlanner.blendHorizontal(
			new Vec3(Double.NaN, 0.2, Double.POSITIVE_INFINITY), 0, 0, 0, 1,
			0);
		assertEquals(new Vec3(0, 0.2, 0), recovered);
		assertEquals(new Vec3(0, 0.2, 0), MovementPlanner.clampHorizontal(
			new Vec3(Double.NaN, 0.2, 1), 1));
	}
}
