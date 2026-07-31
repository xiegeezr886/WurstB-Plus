package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class CombatAimPointPlannerTest
{
	@Test
	void prefersVisiblePointOverThroughWallPoint()
	{
		AABB box = new AABB(2, 0, 0, 3, 2, 1);
		Vec3 preferred = new Vec3(2.5, 1, 0.5);
		CombatAimPointPlanner.AimPoint result = CombatAimPointPlanner.find(box,
			Vec3.ZERO, preferred, 5, 5, point -> point.y > 1.5,
			point -> point.distanceToSqr(preferred));

		assertFalse(result.throughWalls());
		assertTrue(result.point().y > 1.5);
	}

	@Test
	void usesIndependentThroughWallsRange()
	{
		AABB box = new AABB(2, 0, 0, 3, 2, 1);
		CombatAimPointPlanner.AimPoint result = CombatAimPointPlanner.find(box,
			Vec3.ZERO, box.getCenter(), 1, 4, point -> false, point -> 0);

		assertTrue(result.throughWalls());
		assertNull(CombatAimPointPlanner.find(box, Vec3.ZERO, box.getCenter(),
			1, 1, point -> false, point -> 0));
	}
}
