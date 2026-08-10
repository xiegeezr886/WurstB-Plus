package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.wurstclient.clickgui2.component.SuperSoftClickGuiScreen;
import net.wurstclient.clickgui2.screens.EditSliderScreen;
import net.wurstclient.hud2.HudEditorScreen;
import net.wurstclient.options.WurstOptionsScreen;
import org.junit.jupiter.api.Test;

class ScreenRegistryTest
{
	@Test
	void recognizesScreenTypeHierarchy()
	{
		assertTrue(ScreenRegistry.CONTAINER.matchesType(InventoryScreen.class));
		assertTrue(ScreenRegistry.INVENTORY.matchesType(InventoryScreen.class));
		assertFalse(ScreenRegistry.INVENTORY
			.matchesType(AbstractContainerScreen.class));
	}

	@Test
	void rejectsNullScreen()
	{
		assertFalse(ScreenRegistry.CLICK_GUI.matches(null));
		assertFalse(ScreenRegistry.isAny(null, ScreenRegistry.CLICK_GUI,
			ScreenRegistry.NAVIGATOR));
	}

	@Test
	void recognizesAllWurstIngameScreenPackages()
	{
		assertTrue(ScreenRegistry
			.isWurstIngameScreenType(SuperSoftClickGuiScreen.class));
		assertTrue(ScreenRegistry
			.isWurstIngameScreenType(EditSliderScreen.class));
		assertTrue(ScreenRegistry
			.isWurstIngameScreenType(HudEditorScreen.class));
		assertTrue(ScreenRegistry
			.isWurstIngameScreenType(WurstOptionsScreen.class));
		assertFalse(ScreenRegistry
			.isWurstIngameScreenType(InventoryScreen.class));
	}
}
