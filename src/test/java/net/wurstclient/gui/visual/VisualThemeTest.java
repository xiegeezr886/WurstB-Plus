package net.wurstclient.gui.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.wurstclient.clickgui2.supersoft.SuperSoftTheme;
import net.wurstclient.hud2.NotificationSeverity;
import org.junit.jupiter.api.Test;

final class VisualThemeTest
{
	@Test
	void matchesLiquidBounceNextgenFoundationTokens()
	{
		assertEquals(0xFF4677FF, VisualTheme.ACCENT);
		assertEquals(0xFF4DAC68, VisualTheme.SUCCESS);
		assertEquals(0xFFFC4130, VisualTheme.ERROR);
		assertEquals(0xFFefbf04, VisualTheme.WARNING);
		assertEquals("#4677ff", NotificationSeverity.INFO.getColorHex());
	}

	@Test
	void supersoftUsesTheSharedVisualSystem()
	{
		assertEquals(VisualTheme.ACCENT, SuperSoftTheme.ACCENT);
		assertEquals(VisualTheme.TEXT, SuperSoftTheme.TEXT);
		assertEquals(VisualTheme.PANEL, SuperSoftTheme.WINDOW);
		assertEquals(VisualTheme.BORDER, SuperSoftTheme.BORDER);
	}

	@Test
	void colorUtilitiesClampAndPreserveRgb()
	{
		assertEquals(0x004677FF,
			VisualTheme.withAlpha(VisualTheme.ACCENT, -1));
		assertEquals(0x804677FF,
			VisualTheme.withAlpha(VisualTheme.ACCENT, 128));
		assertEquals(0xFF4677FF,
			VisualTheme.withAlpha(VisualTheme.ACCENT, 300));
		assertEquals(VisualTheme.ACCENT,
			VisualTheme.mix(VisualTheme.ERROR, VisualTheme.ACCENT, 1));
		assertTrue(VisualTheme.RADIUS_SMALL <= VisualTheme.RADIUS_MEDIUM);
		assertTrue(VisualTheme.RADIUS_MEDIUM <= VisualTheme.RADIUS_LARGE);
	}
}
