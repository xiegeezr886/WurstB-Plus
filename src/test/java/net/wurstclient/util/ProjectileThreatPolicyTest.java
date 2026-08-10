package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class ProjectileThreatPolicyTest
{
	private static final AABB PLAYER_BOX = new AABB(-0.3, 0, -0.3, 0.3, 1.8,
		0.3);
	private static final AABB PROJECTILE_BOX = new AABB(4.5, 0.75, -0.25,
		5.0, 1.25, 0.25);

	@Test
	void rejectsStationaryAndNonFiniteProjectiles()
	{
		assertFalse(ProjectileThreatPolicy.isThreat(new Vec3(5, 1, 0),
			Vec3.ZERO, PROJECTILE_BOX, PLAYER_BOX));
		assertFalse(ProjectileThreatPolicy.isThreat(
			new Vec3(Double.NaN, 1, 0), new Vec3(-1, 0, 0), PROJECTILE_BOX,
			PLAYER_BOX));
	}

	@Test
	void acceptsProjectileMovingTowardPlayer()
	{
		assertTrue(ProjectileThreatPolicy.isThreat(new Vec3(5, 1, 0),
			new Vec3(-0.5, 0, 0), PROJECTILE_BOX, PLAYER_BOX));
	}

	@Test
	void rejectsProjectileMovingAwayFromPlayer()
	{
		assertFalse(ProjectileThreatPolicy.isThreat(new Vec3(5, 1, 0),
			new Vec3(0.5, 0, 0), PROJECTILE_BOX, PLAYER_BOX));
	}

	@Test
	void trajectoryIntersectionCatchesOffsetApproach()
	{
		Vec3 position = new Vec3(0.56, 2.04, 0);
		Vec3 velocity = new Vec3(-0.01, 0, 0);
		assertTrue(ProjectileThreatPolicy.isThreat(position, velocity,
			new AABB(0.31, 1.79, -0.25, 0.81, 2.29, 0.25), PLAYER_BOX));
	}

	@Test
	void computesSquaredDistanceToBox()
	{
		assertEquals(4, ProjectileThreatPolicy.distanceSquared(
			new Vec3(2.3, 1, 0), PLAYER_BOX), 1.0E-9);
		assertEquals(0, ProjectileThreatPolicy.distanceSquared(
			new Vec3(0, 1, 0), PLAYER_BOX), 1.0E-9);
	}
}
