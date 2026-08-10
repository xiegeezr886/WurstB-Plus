package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RotationSmoothingTest
{
	@Test
	void limitsAngularAccelerationAcrossTicks()
	{
		RotationSmoothing.Step first = RotationSmoothing.smoothWithAcceleration(
			new Rotation(0, 0), new Rotation(90, 0), new Rotation(0, 0), 30,
			5, RotationSmoothing.LINEAR);
		RotationSmoothing.Step second = RotationSmoothing.smoothWithAcceleration(
			first.rotation(), new Rotation(90, 0), first.delta(), 30, 5,
			RotationSmoothing.LINEAR);

		assertEquals(5, first.delta().yaw(), 1.0E-6);
		assertEquals(10, second.delta().yaw(), 1.0E-6);
		assertEquals(15, second.rotation().yaw(), 1.0E-6);
	}

	@Test
	void reversesDirectionWithoutAnInstantVelocityFlip()
	{
		RotationSmoothing.Step step = RotationSmoothing.smoothWithAcceleration(
			new Rotation(20, 0), new Rotation(-90, 0), new Rotation(10, 0),
			30, 5, RotationSmoothing.LINEAR);

		assertEquals(5, step.delta().yaw(), 1.0E-6);
		assertEquals(25, step.rotation().yaw(), 1.0E-6);
	}

	@Test
	void preservesShortestYawPathAndPitchBounds()
	{
		RotationSmoothing.Step step = RotationSmoothing.smoothWithAcceleration(
			new Rotation(179, 89), new Rotation(-179, 100),
			new Rotation(0, 0), 20, 20, RotationSmoothing.LINEAR);

		assertEquals(181, step.rotation().yaw(), 1.0E-6);
		assertTrue(step.rotation().pitch() <= 90);
	}

	@Test
	void rejectsNonFiniteStateWithoutPoisoningTheNextStep()
	{
		RotationSmoothing.Step step = RotationSmoothing.smoothWithAcceleration(
			new Rotation(20, 10),
			new Rotation(Float.NaN, Float.POSITIVE_INFINITY),
			new Rotation(Float.NaN, Float.NEGATIVE_INFINITY), Float.NaN,
			Float.NaN, RotationSmoothing.LINEAR);

		assertEquals(20, step.rotation().yaw(), 1.0E-6);
		assertEquals(10, step.rotation().pitch(), 1.0E-6);
		assertEquals(0, step.delta().yaw(), 1.0E-6);
		assertEquals(0, step.delta().pitch(), 1.0E-6);
	}

	@Test
	void clampsNegativeMaximumChangeToZero()
	{
		Rotation result = RotationSmoothing.smooth(new Rotation(10, 5),
			new Rotation(90, 30), -10, RotationSmoothing.LINEAR);

		assertEquals(10, result.yaw(), 1.0E-6);
		assertEquals(5, result.pitch(), 1.0E-6);
	}
}
