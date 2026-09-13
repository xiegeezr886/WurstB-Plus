package net.wurstclient.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

final class ModuleColorsTest
{
	@Test
	void staticModeIgnoresIndexAndTime()
	{
		ModuleColors colors = new ModuleColors().mode(ModuleColors.Mode.STATIC)
			.staticColor(0xFF112233);
		assertEquals(0xFF112233, colors.colorFor(0, 5, 0));
		assertEquals(0xFF112233, colors.colorFor(4, 5, 9_999));
	}

	@Test
	void gradientInterpolatesFromStartToEnd()
	{
		ModuleColors colors = new ModuleColors()
			.mode(ModuleColors.Mode.GRADIENT)
			.gradient(0xFF000000, 0xFFFFFFFF);
		assertEquals(0xFF000000, colors.colorFor(0, 3, 0));
		assertEquals(0xFFFFFFFF, colors.colorFor(2, 3, 0));
		int middle = colors.colorFor(1, 3, 0);
		assertEquals(0xFF, middle >>> 24);
		assertTrue((middle >> 16 & 0xFF) > 0);
		assertTrue((middle >> 16 & 0xFF) < 255);
	}

	@Test
	void rainbowAndWaveChangeAcrossIndex()
	{
		ModuleColors rainbow = new ModuleColors()
			.mode(ModuleColors.Mode.RAINBOW)
			.rainbow(3, 0.6F, 1, 0.2F);
		assertNotEquals(rainbow.colorFor(0, 4, 1_000),
			rainbow.colorFor(2, 4, 1_000));

		ModuleColors wave = new ModuleColors()
			.mode(ModuleColors.Mode.WAVE)
			.wave(0xFF007CFF, 2);
		assertEquals(0xFF, wave.colorFor(0, 3, 0) >>> 24);
		assertNotEquals(wave.colorFor(0, 3, 0), wave.colorFor(1, 3, 0));
	}

	@Test
	void fillColorsWritesEverySlot()
	{
		List<Integer> out = new ArrayList<>(List.of(0, 0, 0));
		new ModuleColors().mode(ModuleColors.Mode.STATIC)
			.staticColor(0xFFABCDEF).fillColors(out, 0);
		assertEquals(List.of(0xFFABCDEF, 0xFFABCDEF, 0xFFABCDEF), out);
	}

	@Test
	void fadeKeepsRgbAndVariesAlpha()
	{
		int color = new ModuleColors().mode(ModuleColors.Mode.FADE)
			.colorFor(0, 1, 0);
		assertEquals(0xFFFFFF, color & 0xFFFFFF);
		int alpha = color >>> 24;
		assertTrue(alpha >= Math.round(0.4F * 255));
		assertTrue(alpha <= 255);
	}
}
