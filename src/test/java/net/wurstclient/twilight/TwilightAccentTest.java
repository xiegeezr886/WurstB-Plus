/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.twilight.TwilightAccent.Palette;

/**
 * Tests the cover colour extraction that drives the accent colour of the
 * Twilight Echo interface.
 *
 * <p>
 * The reference implementation is not fixed to the blue theme token: the
 * screenshots show a purple play button on one page and a green one on another,
 * so the accent has to follow the artwork while staying readable.
 */
final class TwilightAccentTest
{
	private static final int RED = 0xFFE53935;
	private static final int BLUE = 0xFF1E88E5;
	private static final int GREEN = 0xFF43A047;
	private static final int GREY = 0xFF808080;
	
	@Test
	void aRedCoverGivesARedAccent()
	{
		Palette palette = TwilightAccent.fromPixels(fill(200, RED), 200);
		
		assertTrue(palette.fromCover);
		assertTrue(palette.hue >= 350F || palette.hue <= 15F,
			"hue was " + palette.hue);
		assertTrue(palette.saturation >= 0.45F
			&& palette.saturation <= 0.85F);
		assertTrue(palette.lightness >= 0.42F && palette.lightness <= 0.58F);
	}
	
	@Test
	void aGreyCoverFallsBackToTheThemeBlue()
	{
		Palette palette = TwilightAccent.fromPixels(fill(200, GREY), 200);
		
		assertFalse(palette.fromCover);
		assertTrue(palette.hue > 200F && palette.hue < 240F,
			"hue was " + palette.hue);
		assertEquals(TwilightAccent.hex(TwilightAccent.fallback().accent),
			TwilightAccent.hex(palette.accent));
	}
	
	@Test
	void transparentPixelsAreIgnored()
	{
		int[] pixels = new int[100];
		
		for(int i = 0; i < pixels.length; i++)
			pixels[i] = RED & 0x00FFFFFF;
		
		assertFalse(TwilightAccent.fromPixels(pixels, pixels.length).fromCover);
	}
	
	@Test
	void theDominantHueWins()
	{
		int[] pixels = new int[100];
		
		for(int i = 0; i < 100; i++)
			pixels[i] = i < 60 ? BLUE : GREEN;
		
		Palette palette = TwilightAccent.fromPixels(pixels, pixels.length);
		
		assertTrue(palette.fromCover);
		assertTrue(palette.hue > 195F && palette.hue < 240F,
			"expected the blue hue to win, got " + palette.hue);
	}
	
	@Test
	void theSameCoverAlwaysGivesTheSamePalette()
	{
		int[] pixels = new int[300];
		
		for(int i = 0; i < pixels.length; i++)
			pixels[i] = i % 2 == 0 ? BLUE : 0xFF8E24AA;
		
		Palette first = TwilightAccent.fromPixels(pixels, pixels.length);
		Palette second = TwilightAccent.fromPixels(pixels, pixels.length);
		
		assertEquals(first.accent, second.accent);
		assertEquals(first.heroFrom, second.heroFrom);
		assertEquals(first.onAccent, second.onAccent);
	}
	
	@Test
	void missingInputFallsBack()
	{
		assertFalse(TwilightAccent.fromPixels(null, 10).fromCover);
		assertFalse(TwilightAccent.fromPixels(new int[0], 0).fromCover);
		assertFalse(TwilightAccent.fromPixels(new int[10], -5).fromCover);
	}
	
	@Test
	void onlyTheRequestedPixelsAreRead()
	{
		int[] pixels = new int[20];
		
		for(int i = 0; i < 20; i++)
			pixels[i] = i < 10 ? BLUE : RED;
		
		Palette palette = TwilightAccent.fromPixels(pixels, 10);
		
		assertTrue(palette.hue > 195F && palette.hue < 240F,
			"expected blue, got hue " + palette.hue);
	}
	
	@Test
	void theSoftTintUsesTheAlphaOfTheReference()
	{
		Palette palette = TwilightAccent.fromPixels(fill(50, GREEN), 50);
		
		assertEquals(Math.round(TwilightAccent.SOFT_ALPHA * 255F),
			TwilightAccent.alpha(palette.accentSoft));
		assertEquals(TwilightAccent.hex(palette.accent),
			TwilightAccent.hex(palette.accentSoft));
	}
	
	@Test
	void theHeroGradientIsAVeryLightTint()
	{
		Palette palette = TwilightAccent.fromPixels(fill(50, BLUE), 50);
		
		float[] from = TwilightAccent.toHsl(palette.heroFrom);
		float[] to = TwilightAccent.toHsl(palette.heroTo);
		
		assertTrue(from[2] > 0.85F, "lightness was " + from[2]);
		assertTrue(to[2] >= from[2], "the second stop has to be lighter");
		
		// "subtle" means close to white, not low HSL saturation: a light pink
		// still counts as saturated in HSL
		int highest = Math.max(TwilightAccent.red(palette.heroFrom),
			Math.max(TwilightAccent.green(palette.heroFrom),
				TwilightAccent.blue(palette.heroFrom)));
		int lowest = Math.min(TwilightAccent.red(palette.heroFrom),
			Math.min(TwilightAccent.green(palette.heroFrom),
				TwilightAccent.blue(palette.heroFrom)));
		
		assertTrue(highest - lowest <= 40,
			"the tint has to stay close to white, spread was "
				+ (highest - lowest));
	}
	
	@Test
	void theLabelOnTheAccentStaysReadable()
	{
		Palette light = TwilightAccent.fromPixels(fill(50, BLUE), 50);
		Palette dark = TwilightAccent.fromPixels(fill(50, BLUE), 50, true);
		
		assertTrue(
			TwilightAccent.contrastRatio(light.accent, light.onAccent) >= 4.0D,
			"contrast was "
				+ TwilightAccent.contrastRatio(light.accent, light.onAccent));
		assertTrue(
			TwilightAccent.contrastRatio(dark.accent, dark.onAccent) >= 4.0D);
	}
	
	@Test
	void theDarkThemeWantsALighterAccent()
	{
		// a dark cover, because a cover that is already mid light cannot get any
		// lighter than the light theme clamp
		int darkCover = 0xFF7F1D1D;
		Palette light = TwilightAccent.fromPixels(fill(50, darkCover), 50);
		Palette dark =
			TwilightAccent.fromPixels(fill(50, darkCover), 50, true);
		
		assertTrue(light.fromCover);
		assertTrue(dark.lightness >= 0.55F, "was " + dark.lightness);
		assertTrue(dark.lightness > light.lightness,
			"dark " + dark.lightness + " vs light " + light.lightness);
		assertEquals(light.hue, dark.hue, 0.001F);
	}
	
	@Test
	void saturationAndLightnessAreClamped()
	{
		Palette neon = TwilightAccent.derive(120F, 1F, 0.5F, false, true);
		Palette murky = TwilightAccent.derive(120F, 0.9F, 0.02F, false, true);
		
		assertEquals(0.85F, neon.saturation, 0.0001F);
		assertEquals(0.42F, murky.lightness, 0.0001F);
		assertNotEquals(TwilightAccent.hex(neon.accent),
			TwilightAccent.hex(murky.accent));
	}
	
	@Test
	void aCoverThatIsAlmostBlackStillGivesAUsableAccent()
	{
		Palette palette =
			TwilightAccent.fromPixels(fill(50, 0xFF0A1208), 50);
		
		assertFalse(palette.fromCover);
		assertTrue(palette.lightness >= 0.42F);
	}
	
	@Test
	void hslRoundTrips()
	{
		for(int color : new int[]{RED, BLUE, GREEN, 0xFF123456,
			0xFFF0E0D0})
		{
			float[] hsl = TwilightAccent.toHsl(color);
			int back = TwilightAccent.toRgb(hsl[0], hsl[1], hsl[2]);
			
			assertTrue(
				Math.abs(TwilightAccent.red(color) - TwilightAccent.red(back)) <= 1);
			assertTrue(Math
				.abs(TwilightAccent.green(color) - TwilightAccent.green(back)) <= 1);
			assertTrue(Math
				.abs(TwilightAccent.blue(color) - TwilightAccent.blue(back)) <= 1);
		}
	}
	
	@Test
	void mixingAndAlphaWork()
	{
		assertEquals(0xFFFFFFFF,
			TwilightAccent.mix(0xFF000000, 0xFFFFFFFF, 1F));
		assertEquals(0xFF000000,
			TwilightAccent.mix(0xFF000000, 0xFFFFFFFF, 0F));
		assertEquals(0x80FF0000,
			TwilightAccent.withAlpha(0xFFFF0000, 128F / 255F));
		assertEquals("#2563EB",
			TwilightAccent.hex(TwilightAccent.DEFAULT_ACCENT));
	}
	
	private static int[] fill(int count, int color)
	{
		int[] pixels = new int[count];
		
		for(int i = 0; i < count; i++)
			pixels[i] = color;
		
		return pixels;
	}
}
