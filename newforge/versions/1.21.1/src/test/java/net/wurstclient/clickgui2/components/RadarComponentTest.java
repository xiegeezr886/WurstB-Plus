package net.wurstclient.clickgui2.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.wurstclient.clickgui2.components.RadarComponent.RadarPoint;
import org.junit.jupiter.api.Test;

final class RadarComponentTest
{
	@Test
	void keepsNorthUpWhenRotationIsDisabled()
	{
		RadarPoint point = RadarComponent.project(4, -6, 90, false, 2);
		assertEquals(8, point.x(), 0.0001);
		assertEquals(-12, point.y(), 0.0001);
	}

	@Test
	void keepsPlayerForwardUpWhenRotationIsEnabled()
	{
		RadarPoint point = RadarComponent.project(0, 8, 0, true, 1);
		assertEquals(0, point.x(), 0.0001);
		assertEquals(-8, point.y(), 0.0001);
	}

	@Test
	void clipsPointsToCircularRadarField()
	{
		assertTrue(RadarComponent.isInsideRadar(new RadarPoint(3, 4), 5));
		assertFalse(RadarComponent.isInsideRadar(new RadarPoint(4, 4), 5));
	}
}
