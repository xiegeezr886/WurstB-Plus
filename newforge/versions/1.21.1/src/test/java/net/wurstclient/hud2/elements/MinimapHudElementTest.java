package net.wurstclient.hud2.elements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.level.material.MapColor;
import org.junit.jupiter.api.Test;

final class MinimapHudElementTest
{
	@Test
	void clipsTerrainToCircularMap()
	{
		assertTrue(MinimapHudElement.isInsideMap(48, 48));
		assertFalse(MinimapHudElement.isInsideMap(0, 0));
	}

	@Test
	void shadesTerrainByNorthFacingSlope()
	{
		assertEquals(MapColor.Brightness.HIGH,
			MinimapHudElement.brightnessFor(65, 64));
		assertEquals(MapColor.Brightness.NORMAL,
			MinimapHudElement.brightnessFor(64, 64));
		assertEquals(MapColor.Brightness.LOW,
			MinimapHudElement.brightnessFor(63, 64));
	}

	@Test
	void convertsArgbToNativeImageAbgr()
	{
		assertEquals(0xFF563412,
			MinimapHudElement.toNativeColor(0xFF123456));
	}

	@Test
	void calculatesCircularGridSpans()
	{
		assertEquals(10, MinimapHudElement.circleHalfSpan(0, 10));
		assertEquals(8, MinimapHudElement.circleHalfSpan(6, 10));
		assertEquals(0, MinimapHudElement.circleHalfSpan(10, 10));
	}

	@Test
	void usesCircularBoundsWithoutInformationFooter()
	{
		MinimapHudElement minimap = new MinimapHudElement();
		assertEquals(106, minimap.getWidth());
		assertEquals(106, minimap.getHeight());
	}
}
