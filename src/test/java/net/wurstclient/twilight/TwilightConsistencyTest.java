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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.twilight.TwilightShellLayout.SidebarMode;

/**
 * Guards the border between the three modules of the port.
 *
 * <p>
 * {@link TwilightTheme} holds the tokens of the reference, {@link
 * TwilightShellLayout} turns them into canvas geometry and {@link
 * TwilightAccent} derives the accent from the cover art. They are written by
 * different hands and against different source files, so this test locks the
 * values they have to agree on - and documents the two that deliberately differ
 * because the reference itself has two clamps ({@code --te-menu-width} starts at
 * 132px while {@code SideMenu.vue} starts at 180px).
 */
final class TwilightConsistencyTest
{
	@Test
	void theSidebarClampAgreesBetweenThemeAndLayout()
	{
		assertEquals(TwilightTheme.sidebarWidthPx(1500, false, false),
			TwilightShellLayout.sidebarWidth(1500, SidebarMode.EXPANDED, 1F),
			0.001F);
		assertEquals(216F,
			TwilightShellLayout.sidebarWidth(1500, SidebarMode.EXPANDED, 1F),
			0.001F);
		assertEquals(180F, TwilightTheme.sidebarWidthPx(1000, false, false),
			0.001F);
		assertEquals(180F, TwilightTheme.sidebarWidthPx(700, false, false),
			0.001F);
	}
	
	@Test
	void theNarrowSidebarStatesAgree()
	{
		assertEquals(TwilightShellLayout.SIDEBAR_COMPACT,
			TwilightTheme.sidebarWidthPx(900, true, false), 0.001F);
		assertEquals(TwilightShellLayout.SIDEBAR_ICON_ONLY,
			TwilightTheme.sidebarWidthPx(500, false, true), 0.001F);
		assertEquals(164F, TwilightTheme.sidebarWidthPx(900, true, false),
			0.001F);
		assertEquals(72F, TwilightTheme.sidebarWidthPx(500, false, true),
			0.001F);
	}
	
	@Test
	void theTwoReferenceClampsDifferOnPurpose()
	{
		// base.css --te-menu-width: clamp(132px, 18vw, 216px)
		assertEquals(132F, TwilightTheme.menuWidthPx(700), 0.001F);
		// SideMenu.vue: clamp(180px, 18vw, 216px)
		assertEquals(180F, TwilightTheme.sidebarWidthPx(700, false, false),
			0.001F);
		assertEquals(216F, TwilightTheme.menuWidthPx(5000), 0.001F);
	}
	
	@Test
	void bothThemesReportTheirMode()
	{
		assertFalse(TwilightTheme.light().isDark());
		assertTrue(TwilightTheme.dark().isDark());
		assertTrue(TwilightTheme.of(true).isDark());
		assertFalse(TwilightTheme.of(false).isDark());
	}
	
	@Test
	void theSoftTintAlphaIsTheReferenceAlpha()
	{
		int expected = Math.round(TwilightAccent.SOFT_ALPHA * 255F);
		
		assertEquals(20, expected);
		assertEquals(expected, TwilightAccent
			.alpha(TwilightAccent.withAlpha(TwilightAccent.DEFAULT_ACCENT,
				TwilightAccent.SOFT_ALPHA)));
		assertEquals(expected,
			TwilightAccent.alpha(TwilightTheme
				.withAlpha(TwilightAccent.DEFAULT_ACCENT,
					TwilightAccent.SOFT_ALPHA)));
	}
	
	@Test
	void mixingAgreesBetweenThemeAndAccent()
	{
		int[] colors = {0xFF2563EB, 0xFFF4F4F7, 0xFF111827, 0xFF7C3AED};
		
		for(int from : colors)
			for(int to : colors)
				for(float t : new float[]{0F, 0.25F, 0.5F, 0.88F, 1F})
					assertEquals(TwilightAccent.mix(from, to, t),
						TwilightTheme.mix(from, to, t),
						"mix " + TwilightAccent.hex(from) + " -> "
							+ TwilightAccent.hex(to) + " at " + t);
	}
	
	@Test
	void theFallbackAccentIsTheReferenceNavigationColour()
	{
		// --te-navigation-active-text of the light theme
		assertEquals("#2563EB",
			TwilightAccent.hex(TwilightAccent.DEFAULT_ACCENT));
		assertEquals(0xFF, TwilightAccent.alpha(TwilightAccent.DEFAULT_ACCENT));
	}
	
	@Test
	void theLayoutFollowsTheEffectivePaperLightBlock()
	{
		// paper-light.css lines 115-169, the block that overrides the first one
		assertEquals(13, TwilightShellLayout.RADIUS_ITEM);
		assertEquals(22, TwilightShellLayout.RADIUS_HERO);
		assertEquals(45, TwilightShellLayout.NAV_ITEM_HEIGHT);
		assertEquals(5, TwilightShellLayout.NAV_GAP);
		assertEquals(26, TwilightShellLayout.SIDEBAR_RADIUS_RIGHT);
		assertEquals(22, TwilightShellLayout.PANEL_INSET_Y);
		// measured in the screenshot: the bar is 796..865, not the title bar 54
		assertEquals(70, TwilightShellLayout.PLAYER_BAR_HEIGHT);
		assertEquals(14, TwilightShellLayout.PLAYER_BAR_BOTTOM_MARGIN);
		assertEquals(36, TwilightShellLayout.TOOL_BUTTON);
	}
}
