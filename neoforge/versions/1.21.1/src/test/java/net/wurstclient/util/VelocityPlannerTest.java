package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import net.wurstclient.util.VelocityPlanner.Trigger;
import org.junit.jupiter.api.Test;

final class VelocityPlannerTest
{
	@Test
	void modifiesIncomingAndRetainsCurrentAxes()
	{
		Vec3 modified = VelocityPlanner.modify(new Vec3(2, 3, 4),
			new Vec3(1, 0.5, -1), 0, 0.5, 0.75, 1);

		assertEquals(new Vec3(0.75, 1.5, -0.75), modified);
	}

	@Test
	void appliesAllTriggerAndEnvironmentFilters()
	{
		assertTrue(VelocityPlanner.shouldApply(100, 99, true, true,
			Trigger.ON_GROUND, true, false, false, false, false));
		assertFalse(VelocityPlanner.shouldApply(100, 99, true, false,
			Trigger.ALWAYS, true, false, false, false, false));
		assertFalse(VelocityPlanner.shouldApply(100, 99, false, true,
			Trigger.IN_AIR, true, false, false, false, false));
		assertFalse(VelocityPlanner.shouldApply(100, 99, false, true,
			Trigger.ALWAYS, true, true, false, false, false));
	}

	@Test
	void detectsFallDamageVelocity()
	{
		assertTrue(VelocityPlanner.isFallDamageVelocity(new Vec3(0, -0.1, 0)));
		assertFalse(VelocityPlanner.isFallDamageVelocity(new Vec3(0.1, -0.1, 0)));
	}
}
