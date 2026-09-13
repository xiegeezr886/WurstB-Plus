package net.wurstclient.clickgui2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

final class ClickGuiStyleTest
{
	@Test
	void cyclesEpsilonToSuperSoftToVape()
	{
		assertSame(ClickGuiStyle.SUPERSOFT, ClickGuiStyle.EPSILON.next());
		assertSame(ClickGuiStyle.VAPE, ClickGuiStyle.SUPERSOFT.next());
		assertSame(ClickGuiStyle.EPSILON, ClickGuiStyle.VAPE.next());
	}

	@Test
	void parsesNamesDisplayNamesAndLegacyVapeFlag()
	{
		assertSame(ClickGuiStyle.EPSILON, ClickGuiStyle.fromString(null));
		assertSame(ClickGuiStyle.EPSILON, ClickGuiStyle.fromString(""));
		assertSame(ClickGuiStyle.SUPERSOFT,
			ClickGuiStyle.fromString("supersoft"));
		assertSame(ClickGuiStyle.VAPE, ClickGuiStyle.fromString("Vape"));
		assertSame(ClickGuiStyle.VAPE, ClickGuiStyle.fromString("true"));
		assertSame(ClickGuiStyle.EPSILON, ClickGuiStyle.fromString("nope"));
	}

	@Test
	void displayNameIsStableForSettings()
	{
		assertEquals("Epsilon", ClickGuiStyle.EPSILON.toString());
		assertEquals("SuperSoft", ClickGuiStyle.SUPERSOFT.displayName());
	}
}
