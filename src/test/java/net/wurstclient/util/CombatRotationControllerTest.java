package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CombatRotationControllerTest
{
	@Test
	void ownsAccelerationHistoryAcrossPlanningSteps()
	{
		CombatRotationController controller = new CombatRotationController(
			RotationQueue.Priority.COMBAT);
		Rotation first = controller.plan(new Rotation(0, 0),
			new Rotation(90, 30), 10, 5, RotationSmoothing.LINEAR);

		assertEquals(5, first.yaw(), 0.001);
		assertEquals(3.5, first.pitch(), 0.001);
		assertEquals(5, controller.getDelta().yaw(), 0.001);
		Rotation second = controller.plan(new Rotation(0, 0),
			new Rotation(90, 30), 10, 5, RotationSmoothing.LINEAR);
		assertEquals(15, second.yaw(), 0.001);
	}

	@Test
	void exactPlanningSanitizesInvalidTargetRotation()
	{
		CombatRotationController controller = new CombatRotationController(
			RotationQueue.Priority.COMBAT);
		Rotation planned = controller.planExact(new Rotation(20, 95),
			new Rotation(Float.NaN, Float.POSITIVE_INFINITY));

		assertTrue(Float.isFinite(planned.yaw()));
		assertEquals(20, planned.yaw(), 0.001);
		assertEquals(90, planned.pitch(), 0.001);
	}
}
