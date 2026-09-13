package net.wurstclient.clickgui2.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlatThemeTest
{
	private final FlatTheme theme = new FlatTheme();

	@BeforeEach
	void setUp()
	{
		theme.update(new float[]{0, 0, 0}, new float[]{1, 0, 0},
			0xFFABCDEF, 1, 0.8F);
	}

	@Test
	void mixesBackgroundAndAccent()
	{
		assertEquals(0xFF800000, theme.mix(0.5F, 1));
	}

	@Test
	void clampsColorInputs()
	{
		assertEquals(0xFFFF0000, theme.accent(2));
		assertEquals(0x00000000, theme.background(-1));
	}

	@Test
	void appliesTextOpacityWithoutChangingRgb()
	{
		assertEquals(0x80ABCDEF, theme.text(0.5F));
	}

	@Test
	void activeControlsUseAccentColor()
	{
		int fill = theme.controlFill(0, true);
		int alpha = fill >>> 24;
		int r = fill >> 16 & 0xFF;
		int g = fill >> 8 & 0xFF;
		int b = fill & 0xFF;
		// active control should contain accent (red=1) and be mostly opaque
		org.junit.jupiter.api.Assertions.assertTrue(alpha > 160,
			"Active control should be mostly opaque");
		org.junit.jupiter.api.Assertions.assertTrue(r > g
			&& r > b, "Active control should include accent color (red)");
	}
}
