/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.twilight.TwilightAccent.Palette;

/**
 * Tests the cover crop and the palette that follows the playing track.
 *
 * <p>
 * The reference shows album art in fixed boxes with {@code object-fit: cover},
 * and its accent colour comes from the artwork. Both are pure arithmetic here so
 * they can be checked without a texture or a running player.
 */
final class TwilightCoverAccentTest
{
	@Test
	void aWideCoverIsCroppedAtTheSides()
	{
		// 1000x500 artwork in a 100x100 box keeps the full height
		int[] rect = TwilightCoverFit.squareRect(1000, 500, 100);
		
		assertEquals(0, rect[1]);
		assertEquals(500, rect[3]);
		assertEquals(500, rect[2]);
		assertEquals(250, rect[0]);
	}
	
	@Test
	void aTallCoverIsCroppedAtTheTopAndBottom()
	{
		// 500x1000 artwork in a 100x100 box keeps the full width
		int[] rect = TwilightCoverFit.squareRect(500, 1000, 100);
		
		assertEquals(0, rect[0]);
		assertEquals(500, rect[2]);
		assertEquals(500, rect[3]);
		assertEquals(250, rect[1]);
	}
	
	@Test
	void aSquareCoverIsUsedAsItIs()
	{
		int[] rect = TwilightCoverFit.squareRect(300, 300, 64);
		
		assertEquals(0, rect[0]);
		assertEquals(0, rect[1]);
		assertEquals(300, rect[2]);
		assertEquals(300, rect[3]);
	}
	
	@Test
	void theCropKeepsTheBoxAspect()
	{
		for(int[] target : new int[][]{{100, 100}, {200, 100}, {100, 200},
			{44, 44}})
			for(int[] source : new int[][]{{1000, 500}, {500, 1000},
				{640, 640}, {1920, 1080}})
			{
				int[] rect = TwilightCoverFit.sourceRect(source[0], source[1],
					target[0], target[1]);
				double wanted = target[0] / (double)target[1];
				double got = rect[2] / (double)rect[3];
				
				assertTrue(Math.abs(wanted - got) < 0.02D,
					"aspect " + got + " vs " + wanted);
				assertTrue(rect[0] >= 0 && rect[1] >= 0);
				assertTrue(rect[0] + rect[2] <= source[0]);
				assertTrue(rect[1] + rect[3] <= source[1]);
			}
	}
	
	@Test
	void degenerateSizesGiveAnEmptyRectangle()
	{
		assertEquals(0, TwilightCoverFit.sourceRect(0, 100, 10, 10)[2]);
		assertEquals(0, TwilightCoverFit.sourceRect(100, 100, 0, 10)[2]);
		assertEquals(0, TwilightCoverFit.sourceRect(-5, -5, -1, -1)[2]);
	}
	
	@Test
	void aKnownAccentBecomesAPalette()
	{
		// the project's own default accent
		Palette palette = TwilightAccent.fromAccent(0xFF007CFF, false);
		
		assertTrue(palette.fromCover);
		assertTrue(TwilightAccent.contrastRatio(palette.accent,
			palette.onAccent) >= 4.0D);
		assertTrue(palette.hue > 200F && palette.hue < 240F,
			"hue was " + palette.hue);
	}
	
	@Test
	void aKnownAccentIsNormalisedLikeASampledOne()
	{
		// an extremely dark and an extremely bright accent both end up usable
		Palette dark = TwilightAccent.fromAccent(0xFF000814, false);
		Palette bright = TwilightAccent.fromAccent(0xFFFFFDE0, false);
		
		assertTrue(dark.lightness >= 0.42F, "was " + dark.lightness);
		assertTrue(bright.lightness <= 0.58F, "was " + bright.lightness);
		assertTrue(dark.saturation >= 0.45F || dark.hue == 0F);
	}
	
	@Test
	void theDarkThemeVariantOfAKnownAccentIsLighter()
	{
		int accent = 0xFF7C3AED;
		Palette light = TwilightAccent.fromAccent(accent, false);
		Palette dark = TwilightAccent.fromAccent(accent, true);
		
		assertTrue(dark.lightness >= light.lightness);
		assertEquals(light.hue, dark.hue, 0.001F);
	}
}
