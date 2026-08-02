package net.wurstclient.hud2.elements;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ComboHudElementTest
{
	@Test
	void repeatedAttacksIncreaseCombo()
	{
		ComboHudElement element = new ComboHudElement();
		element.onPlayerAttacksEntity(null);
		element.onPlayerAttacksEntity(null);

		assertEquals("Combo: 2", element.getText());
	}
}
