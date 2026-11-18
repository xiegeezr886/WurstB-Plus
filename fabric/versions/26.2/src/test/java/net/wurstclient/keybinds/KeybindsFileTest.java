package net.wurstclient.keybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class KeybindsFileTest
{
	@Test
	void restoresRightShiftNavigatorAfterMergedGuiMigration()
	{
		assertEquals("navigator", KeybindsFile.migrateGuiBinding(
			"key.keyboard.right.shift", "clickgui"));
	}

	@Test
	void preservesRightControlAndCustomBindings()
	{
		assertEquals("clickgui", KeybindsFile.migrateGuiBinding(
			"key.keyboard.right.control", "clickgui"));
		assertEquals("killaura", KeybindsFile.migrateGuiBinding(
			"key.keyboard.right.shift", "killaura"));
	}
}
