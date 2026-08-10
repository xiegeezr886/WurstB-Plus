package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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

	@Test
	void rejectsNonFiniteInputsAndScores()
	{
		AABB box = new AABB(2, 0, 0, 3, 2, 1);
		assertNull(CombatAimPointPlanner.find(box, Vec3.ZERO,
			new Vec3(Double.NaN, 0, 0), 5, 5, point -> true, point -> 0));
		assertNull(CombatAimPointPlanner.find(box, Vec3.ZERO, box.getCenter(),
			Double.POSITIVE_INFINITY, 5, point -> true, point -> 0));

		CombatAimPointPlanner.AimPoint result = CombatAimPointPlanner.find(box,
			Vec3.ZERO, box.getCenter(), 5, 5, point -> true,
			point -> point.x < 2.8 ? Double.NaN : point.x);
		assertTrue(result.point().x >= 2.8);
	}

	@Test
	void skipsDuplicateAndOutOfRangeRaycasts()
	{
		AABB box = new AABB(0, 0, 0, 2, 2, 2);
		Set<Vec3> visited = new HashSet<>();
		CombatAimPointPlanner.find(box, Vec3.ZERO, box.getCenter(), 5, 5,
			point -> {
				assertTrue(visited.add(point));
				return true;
			}, point -> 0);

		AtomicInteger calls = new AtomicInteger();
		assertNull(CombatAimPointPlanner.find(new AABB(100, 0, 0, 101, 1, 1),
			Vec3.ZERO, new Vec3(100.5, 0.5, 0.5), 1, 1, point -> {
				calls.incrementAndGet();
				return true;
			}, point -> 0));
		assertEquals(0, calls.get());
	}
}
