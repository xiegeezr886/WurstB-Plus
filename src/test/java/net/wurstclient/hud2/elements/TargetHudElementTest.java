package net.wurstclient.hud2.elements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class TargetHudElementTest
{
	@Test
	void usesRiseModernDimensions()
	{
		TargetHudElement element = new TargetHudElement();
		assertEquals(145, element.getWidth());
		assertEquals(48, element.getHeight());
	}

	@Test
	void widthFollowsRiseModernTextFormula()
	{
		assertEquals(65, TargetHudElement.healthBarWidth(40, 20));
		assertEquals(75, TargetHudElement.healthBarWidth(60, 20));
		assertEquals(145, TargetHudElement.panelWidth(40, 20));
		assertEquals(155, TargetHudElement.panelWidth(60, 20));
	}

	@Test
	void healthIsRoundedToOneDecimal()
	{
		assertEquals("19.9", TargetHudElement.formatHealth(19.94F));
		assertEquals("20.0", TargetHudElement.formatHealth(20));
	}

	@Test
	void healthAnimationUsesRiseQuintCurve()
	{
		assertEquals(10.3125F, TargetHudElement.interpolateHealth(20, 10,
			TargetHudElement.HEALTH_ANIMATION_NANOS / 2), 0.001F);
		assertEquals(10, TargetHudElement.interpolateHealth(20, 10,
			TargetHudElement.HEALTH_ANIMATION_NANOS), 0.001F);
	}

	@Test
	void openingAnimationHasElasticOvershoot()
	{
		assertEquals(0, TargetHudElement.easeOutElastic(0));
		assertTrue(TargetHudElement.easeOutElastic(0.15F) > 1);
		assertEquals(1, TargetHudElement.easeOutElastic(1));
	}

	@Test
	void closingAnimationUsesBackOvershoot()
	{
		assertEquals(1, TargetHudElement.exitScale(0));
		assertTrue(TargetHudElement.exitScale(0.2F) > 1);
		assertEquals(0, TargetHudElement.exitScale(1));
	}
}
