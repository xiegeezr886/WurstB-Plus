package net.wurstclient.hud2.elements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

final class TargetHudElementTest
{
	@Test
	void usesCompactPanelDimensions()
	{
		TargetHudElement element = new TargetHudElement();
		assertEquals(150, element.getWidth());
		assertEquals(38, element.getHeight());
	}

	@Test
	void expandsOnlyWhenEquipmentIsVisible()
	{
		assertEquals(38, TargetHudElement.heightForEquipmentCount(0));
		assertEquals(58, TargetHudElement.heightForEquipmentCount(1));
		assertEquals(58, TargetHudElement.heightForEquipmentCount(5));
	}

	@Test
	void equipmentRowUsesCompactFixedSpacing()
	{
		assertEquals(0, TargetHudElement.equipmentRowWidth(0));
		assertEquals(16, TargetHudElement.equipmentRowWidth(1));
		assertEquals(88, TargetHudElement.equipmentRowWidth(5));
	}

	@Test
	void fullHealthUsesThemeColor()
	{
		int themeColor = 0xFF006366;
		assertEquals(themeColor, TargetHudElement.healthColor(themeColor, 1));
		assertEquals(0xFFFF4130,
			TargetHudElement.healthColor(themeColor, 0));
	}

	@Test
	void targetStaysVisibleThenFadesOut()
	{
		long lastVisible = 1_000_000_000L;
		assertEquals(1, TargetHudElement.calculateOpacity(lastVisible
			+ TargetHudElement.HOLD_NANOS, lastVisible));
		float halfway = TargetHudElement.calculateOpacity(lastVisible
			+ TargetHudElement.HOLD_NANOS
			+ TargetHudElement.FADE_NANOS / 2, lastVisible);
		assertTrue(halfway > 0 && halfway < 1);
		assertEquals(0, TargetHudElement.calculateOpacity(lastVisible
			+ TargetHudElement.HOLD_NANOS + TargetHudElement.FADE_NANOS,
			lastVisible));
	}

	@Test
	void healthAnimationApproachesLatestValue()
	{
		float halfway = TargetHudElement.smoothHealth(20, 10, 90_000_000L);
		assertEquals(15, halfway, 0.01F);
		assertEquals(10,
			TargetHudElement.smoothHealth(20, 10, 180_000_000L), 0.01F);
	}

	@Test
	void uuidUsesStableCompactForm()
	{
		UUID uuid = UUID.fromString("12345678-1234-5678-9abc-def012345678");
		assertEquals("12345678", TargetHudElement.compactUuid(uuid));
	}
}
