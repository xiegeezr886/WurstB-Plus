/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.epsilon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 锁住暗色调色板与派生色公式。颜色抄错不会有编译错误，只会在游戏里看起来不对，
 * 所以这里把参考项目的真值写成断言。
 */
public final class EpsilonPanelThemeTest
{
	@Test
	public void darkPaletteMatchesTheReference()
	{
		assertEquals(0xEE141218, EpsilonPanelTheme.SURFACE);
		assertEquals(0xE81B1820, EpsilonPanelTheme.SURFACE_DIM);
		assertEquals(0xF4211F26, EpsilonPanelTheme.SURFACE_CONTAINER);
		assertEquals(0xF01B1820, EpsilonPanelTheme.SURFACE_CONTAINER_LOW);
		assertEquals(0xF82B2930, EpsilonPanelTheme.SURFACE_CONTAINER_HIGH);
		assertEquals(0xFC35333B, EpsilonPanelTheme.SURFACE_CONTAINER_HIGHEST);
		assertEquals(0xB4938F99, EpsilonPanelTheme.OUTLINE);
		assertEquals(0x60938F99, EpsilonPanelTheme.OUTLINE_SOFT);
		assertEquals(0xFFD0BCFF, EpsilonPanelTheme.PRIMARY);
		assertEquals(0xFF381E72, EpsilonPanelTheme.ON_PRIMARY);
		assertEquals(0xEC4F378B, EpsilonPanelTheme.PRIMARY_CONTAINER);
		assertEquals(0xFFEADDFF, EpsilonPanelTheme.ON_PRIMARY_CONTAINER);
		assertEquals(0xEC4A4458, EpsilonPanelTheme.SECONDARY_CONTAINER);
		assertEquals(0xFFE8DEF8, EpsilonPanelTheme.ON_SECONDARY_CONTAINER);
		assertEquals(0xFFECE6F0, EpsilonPanelTheme.TEXT_PRIMARY);
		assertEquals(0xFFCAC4D0, EpsilonPanelTheme.TEXT_SECONDARY);
		assertEquals(0xFF938F99, EpsilonPanelTheme.TEXT_MUTED);
		assertEquals(0xFFF2B8B5, EpsilonPanelTheme.ERROR);
	}
	
	@Test
	public void sizeConstantsMatchTheReference()
	{
		assertEquals(17, EpsilonPanelTheme.PANEL_RADIUS);
		assertEquals(13, EpsilonPanelTheme.SECTION_RADIUS);
		assertEquals(9, EpsilonPanelTheme.CARD_RADIUS);
		assertEquals(7, EpsilonPanelTheme.CONTROL_RADIUS);
		assertEquals(5.0F, EpsilonPanelTheme.OUTER_PADDING);
		assertEquals(3.0F, EpsilonPanelTheme.SECTION_GAP);
		assertEquals(3.0F, EpsilonPanelTheme.ROW_GAP);
		assertEquals(26.0F, EpsilonPanelTheme.SWITCH_WIDTH);
		assertEquals(16.0F, EpsilonPanelTheme.SWITCH_HEIGHT);
	}
	
	@Test
	public void mixHitsBothEndsAndClamps()
	{
		int from = 0x10203040;
		int to = 0xF0E0D0C0;
		
		assertEquals(from, EpsilonPanelTheme.mix(from, to, 0F));
		assertEquals(to, EpsilonPanelTheme.mix(from, to, 1F));
		assertEquals(from, EpsilonPanelTheme.mix(from, to, -5F));
		assertEquals(to, EpsilonPanelTheme.mix(from, to, 5F));
		// 逐通道插值，含 alpha：每通道都是 (0x10+0xF0)/2 这类中点
		assertEquals(0x80808080, EpsilonPanelTheme.mix(from, to, 0.5F));
	}
	
	@Test
	public void withAlphaKeepsTheColour()
	{
		assertEquals(0x00D0BCFF,
			EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.PRIMARY, 0));
		assertEquals(0xFFD0BCFF,
			EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.PRIMARY, 255));
		assertEquals(0x80D0BCFF,
			EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.PRIMARY, 128));
		assertEquals(0x80D0BCFF,
			EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.PRIMARY, 0.5F));
		// 越界被夹取，不会溢出到颜色通道
		assertEquals(0xFFD0BCFF,
			EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.PRIMARY, 999));
		assertEquals(0x00D0BCFF,
			EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.PRIMARY, -5));
	}
	
	@Test
	public void stateLayerScalesTheAlpha()
	{
		assertEquals(0x00D0BCFF,
			EpsilonPanelTheme.stateLayer(EpsilonPanelTheme.PRIMARY, 0F, 20));
		assertEquals(0x14D0BCFF,
			EpsilonPanelTheme.stateLayer(EpsilonPanelTheme.PRIMARY, 1F, 20));
		assertEquals(0x0AD0BCFF,
			EpsilonPanelTheme.stateLayer(EpsilonPanelTheme.PRIMARY, 0.5F, 20));
	}
	
	@Test
	public void rowSurfaceInterpolatesTowardsHigh()
	{
		assertEquals(EpsilonPanelTheme.SURFACE_CONTAINER,
			EpsilonPanelTheme.rowSurface(0F));
		assertEquals(EpsilonPanelTheme.SURFACE_CONTAINER_HIGH,
			EpsilonPanelTheme.rowSurface(1F));
	}
	
	@Test
	public void switchColoursFollowTheProgress()
	{
		assertEquals(EpsilonPanelTheme.SURFACE_CONTAINER_HIGHEST,
			EpsilonPanelTheme.switchTrack(0F));
		assertEquals(EpsilonPanelTheme.PRIMARY,
			EpsilonPanelTheme.switchTrack(1F));
		assertEquals(EpsilonPanelTheme.OUTLINE,
			EpsilonPanelTheme.switchKnob(0F));
		assertEquals(EpsilonPanelTheme.ON_PRIMARY,
			EpsilonPanelTheme.switchKnob(1F));
	}
	
	@Test
	public void switchOutlineFadesOutWhenOn()
	{
		// 关着 = alpha 168，全开 = 完全透明
		assertEquals(168, EpsilonPanelTheme.switchOutline(0F, 0F) >>> 24);
		assertEquals(0, EpsilonPanelTheme.switchOutline(0F, 1F) >>> 24);
		assertEquals(84, EpsilonPanelTheme.switchOutline(0F, 0.5F) >>> 24);
		// 开关描边只在关着时可见，且不改变 RGB
		assertEquals(EpsilonPanelTheme.OUTLINE & 0xFFFFFF,
			EpsilonPanelTheme.switchOutline(0F, 0F) & 0xFFFFFF);
		// hover 时向文字色靠拢
		int hovered = EpsilonPanelTheme.switchOutline(1F, 0F);
		assertEquals(168, hovered >>> 24);
		assertEquals(EpsilonPanelTheme.mix(EpsilonPanelTheme.OUTLINE,
			EpsilonPanelTheme.TEXT_PRIMARY, 0.35F) & 0xFFFFFF,
			hovered & 0xFFFFFF);
	}
	
	@Test
	public void scrollThumbFadesFromOutlineToPrimary()
	{
		assertEquals(EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.OUTLINE, 64),
			EpsilonPanelTheme.scrollThumb(0F));
		assertEquals(EpsilonPanelTheme.withAlpha(EpsilonPanelTheme.PRIMARY, 190),
			EpsilonPanelTheme.scrollThumb(1F));
	}
	
	@Test
	public void segmentedSurfaceUsesTheHighContainer()
	{
		assertEquals(EpsilonPanelTheme.SURFACE_CONTAINER_HIGH,
			EpsilonPanelTheme.segmentedSurface());
	}
}
