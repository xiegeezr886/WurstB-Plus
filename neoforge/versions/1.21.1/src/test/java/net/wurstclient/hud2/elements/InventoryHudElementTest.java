package net.wurstclient.hud2.elements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.hud2.HudLayout.HudElementConfig;

final class InventoryHudElementTest
{
	@Test
	void usesCompactThreeByNineLayout()
	{
		InventoryHudElement element = new InventoryHudElement();
		assertEquals(164, element.getWidth());
		assertEquals(68, element.getHeight());
		assertEquals(9, InventoryHudElement.COLUMNS);
		assertEquals(3, InventoryHudElement.ROWS);
	}

	@Test
	void mapsOnlyMainInventorySlots()
	{
		assertEquals(9, InventoryHudElement.slotIndex(0, 0));
		assertEquals(17, InventoryHudElement.slotIndex(0, 8));
		assertEquals(18, InventoryHudElement.slotIndex(1, 0));
		assertEquals(35, InventoryHudElement.slotIndex(2, 8));
	}

	@Test
	void keepsSlotsInsidePanel()
	{
		assertEquals(6, InventoryHudElement.slotX(0));
		assertEquals(142, InventoryHudElement.slotX(8));
		assertEquals(14, InventoryHudElement.slotY(0));
		assertEquals(48, InventoryHudElement.slotY(2));
	}

	@Test
	void supportsEditorPreviewAndStartsDisabled()
	{
		InventoryHudElement element = new InventoryHudElement();
		HudElementConfig config = element.getDefaultLayout();
		assertTrue(element.renderEditorPreview());
		assertFalse(config.isEnabled());
		assertEquals(HudElementConfig.HORIZONTAL_RIGHT,
			config.getHorizontalAlignment());
		assertEquals(HudElementConfig.VERTICAL_BOTTOM,
			config.getVerticalAlignment());
	}
}
