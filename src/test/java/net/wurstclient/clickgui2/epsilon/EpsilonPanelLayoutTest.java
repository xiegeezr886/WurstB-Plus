/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.epsilon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.clickgui2.epsilon.EpsilonPanelLayout.Layout;
import net.wurstclient.clickgui2.epsilon.EpsilonPanelLayout.Rect;

/**
 * 覆盖中央面板 GUI 的纯几何：夹取、居中、三栏、以及各内部元素的相对位置。
 * 参考项目自己不做 GUI 测试，这里补上，免得手抄常量时抄错。
 */
public final class EpsilonPanelLayoutTest
{
	private static final float DELTA = 0.001F;
	
	@Test
	public void clampsPanelSize()
	{
		assertEquals(584.0F, EpsilonPanelLayout.panelWidth(2000), DELTA);
		assertEquals(584.0F, EpsilonPanelLayout.panelWidth(1600), DELTA);
		assertEquals(560.0F, EpsilonPanelLayout.panelWidth(1000), DELTA);
		// 0.56*940 = 526.4，被下限抬到 528
		assertEquals(528.0F, EpsilonPanelLayout.panelWidth(940), DELTA);
		assertEquals(528.0F, EpsilonPanelLayout.panelWidth(320), DELTA);
		
		assertEquals(324.0F, EpsilonPanelLayout.panelHeight(1080), DELTA);
		assertEquals(324.0F, EpsilonPanelLayout.panelHeight(1000), DELTA);
		assertEquals(300.0F, EpsilonPanelLayout.panelHeight(400), DELTA);
		assertEquals(300.0F, EpsilonPanelLayout.panelHeight(200), DELTA);
	}
	
	@Test
	public void centresThePanel()
	{
		Layout layout = EpsilonPanelLayout.compute(1000, 800, 120);
		
		assertEquals(560.0F, layout.panel().width(), DELTA);
		assertEquals(324.0F, layout.panel().height(), DELTA);
		assertEquals(220.0F, layout.panel().x(), DELTA);
		assertEquals(238.0F, layout.panel().y(), DELTA);
		// 居中：左右与上下留白相等
		assertEquals(layout.panel().x(),
			1000 - layout.panel().right(), DELTA);
		assertEquals(layout.panel().y(),
			800 - layout.panel().bottom(), DELTA);
	}
	
	@Test
	public void laysOutThreeColumns()
	{
		Layout layout = EpsilonPanelLayout.compute(1000, 800, 120);
		Rect panel = layout.panel();
		Rect rail = layout.rail();
		Rect modules = layout.modules();
		Rect detail = layout.detail();
		
		// rail 贴着面板左侧，上下各留 OUTER_PADDING
		assertEquals(panel.x() + 5.0F, rail.x(), DELTA);
		assertEquals(panel.y() + 5.0F, rail.y(), DELTA);
		assertEquals(panel.height() - 10.0F, rail.height(), DELTA);
		assertEquals(120.0F, rail.width(), DELTA);
		
		// 三栏之间是 SECTION_GAP
		assertEquals(rail.right() + 3.0F, modules.x(), DELTA);
		assertEquals(modules.right() + 3.0F, detail.x(), DELTA);
		
		// 中栏宽 = min(164, panelW*0.292)
		assertEquals(Math.min(164.0F, 560.0F * 0.292F), modules.width(),
			DELTA);
		// 详情栏吃掉剩下的宽度，右缘回到面板内边距
		assertEquals(panel.right() - 5.0F, detail.right(), DELTA);
		
		// 三栏同高同顶
		assertEquals(rail.y(), modules.y(), DELTA);
		assertEquals(rail.y(), detail.y(), DELTA);
		assertEquals(rail.height(), detail.height(), DELTA);
	}
	
	@Test
	public void collapsedRailGivesTheDetailColumnMoreRoom()
	{
		assertEquals(42.0F, EpsilonPanelLayout.railWidth(0F), DELTA);
		assertEquals(120.0F, EpsilonPanelLayout.railWidth(1F), DELTA);
		assertEquals(81.0F, EpsilonPanelLayout.railWidth(0.5F), DELTA);
		// 越界输入被夹取
		assertEquals(42.0F, EpsilonPanelLayout.railWidth(-3F), DELTA);
		assertEquals(120.0F, EpsilonPanelLayout.railWidth(9F), DELTA);
		
		Rect wide = EpsilonPanelLayout.compute(1000, 800, 42).detail();
		Rect narrow = EpsilonPanelLayout.compute(1000, 800, 120).detail();
		assertEquals(78.0F, wide.width() - narrow.width(), DELTA);
	}
	
	@Test
	public void railItemsStepDownTheRail()
	{
		Rect rail = EpsilonPanelLayout.compute(1000, 800, 120).rail();
		
		Rect first = EpsilonPanelLayout.railItem(rail, 0);
		assertEquals(rail.x() + 5.0F, first.x(), DELTA);
		assertEquals(rail.y() + 40.0F, first.y(), DELTA);
		assertEquals(rail.width() - 10.0F, first.width(), DELTA);
		assertEquals(34.0F, first.height(), DELTA);
		
		assertEquals(first.y() + 38.0F,
			EpsilonPanelLayout.railItem(rail, 1).y(), DELTA);
		assertEquals(first.y() + 38.0F * 7.0F,
			EpsilonPanelLayout.railItem(rail, 7).y(), DELTA);
	}
	
	@Test
	public void railSettingsItemSticksToTheBottom()
	{
		Rect rail = EpsilonPanelLayout.compute(1000, 800, 120).rail();
		Rect settings = EpsilonPanelLayout.railSettingsItem(rail);
		
		assertEquals(rail.bottom() - 39.0F, settings.y(), DELTA);
		assertEquals(rail.bottom() - 5.0F, settings.bottom(), DELTA);
		assertEquals(34.0F, settings.height(), DELTA);
		// 它与分类列表之间不加分隔线，只靠贴底；这里确认标签页不会被顶到 rail 外
		assertTrue(EpsilonPanelLayout.railItem(rail, 5).bottom()
			< settings.y());
	}
	
	@Test
	public void menuButtonAndSearchBoxSitInTheirCorners()
	{
		Layout layout = EpsilonPanelLayout.compute(1000, 800, 120);
		Rect menu = EpsilonPanelLayout.railMenuButton(layout.rail());
		
		assertEquals(layout.rail().x() + 6.0F, menu.x(), DELTA);
		assertEquals(layout.rail().y() + 4.0F, menu.y(), DELTA);
		assertEquals(28.0F, menu.width(), DELTA);
		assertEquals(28.0F, menu.height(), DELTA);
		
		Rect search = EpsilonPanelLayout.searchBox(layout.modules());
		assertEquals(layout.modules().right() - 6.0F, search.right(), DELTA);
		assertEquals(layout.modules().y() + 8.0F, search.y(), DELTA);
		assertEquals(76.0F, search.width(), DELTA);
		assertEquals(18.0F, search.height(), DELTA);
	}
	
	@Test
	public void listViewportLeavesRoomForTheHeader()
	{
		Rect column = new Rect(100.0F, 200.0F, 160.0F, 300.0F);
		Rect viewport = EpsilonPanelLayout.listViewport(column);
		
		assertEquals(103.0F, viewport.x(), DELTA);
		assertEquals(234.0F, viewport.y(), DELTA);
		assertEquals(154.0F, viewport.width(), DELTA);
		assertEquals(260.0F, viewport.height(), DELTA);
	}
	
	@Test
	public void moduleRowsFollowTheScrollAndDropTheScrollbarWidth()
	{
		Rect viewport = new Rect(100.0F, 200.0F, 160.0F, 300.0F);
		
		Rect first = EpsilonPanelLayout.moduleRow(viewport, 0, 0F, false);
		assertEquals(100.0F, first.x(), DELTA);
		assertEquals(200.0F, first.y(), DELTA);
		assertEquals(160.0F, first.width(), DELTA);
		
		assertEquals(200.0F + 37.0F,
			EpsilonPanelLayout.moduleRow(viewport, 1, 0F, false).y(), DELTA);
		// 滚过一整行间距后，第 2 行正好顶到视口上缘
		assertEquals(200.0F,
			EpsilonPanelLayout.moduleRow(viewport, 1, 37F, false).y(), DELTA);
		// 出现滚动条时行宽减 10
		assertEquals(150.0F,
			EpsilonPanelLayout.moduleRow(viewport, 0, 0F, true).width(), DELTA);
		
		assertEquals(5.0F * 37.0F,
			EpsilonPanelLayout.moduleContentHeight(5), DELTA);
		assertEquals(0.0F, EpsilonPanelLayout.moduleContentHeight(-2), DELTA);
	}
	
	@Test
	public void moduleSwitchIsTrailingAndVerticallyCentred()
	{
		Rect row = new Rect(100.0F, 200.0F, 150.0F, 34.0F);
		Rect toggle = EpsilonPanelLayout.moduleSwitch(row);
		
		assertEquals(row.right() - 5.0F, toggle.right(), DELTA);
		assertEquals(26.0F, toggle.width(), DELTA);
		assertEquals(16.0F, toggle.height(), DELTA);
		assertEquals(row.y() + 9.0F, toggle.y(), DELTA);
	}
	
	@Test
	public void detailHeaderHoldsTheKeybindBlockAndTwoSegments()
	{
		Rect detail = new Rect(400.0F, 200.0F, 300.0F, 300.0F);
		Rect header = EpsilonPanelLayout.detailHeader(detail);
		Rect keybind = EpsilonPanelLayout.detailKeybindBlock(header);
		Rect bind = EpsilonPanelLayout.detailBindSegment(header);
		Rect visibility = EpsilonPanelLayout.detailVisibilitySegment(header);
		
		assertEquals(detail.x() + 3.0F, header.x(), DELTA);
		assertEquals(detail.y() + 34.0F, header.y(), DELTA);
		assertEquals(294.0F, header.width(), DELTA);
		assertEquals(36.0F, header.height(), DELTA);
		
		assertEquals(header.x() + 8.0F, keybind.x(), DELTA);
		assertEquals(18.0F, keybind.width(), DELTA);
		assertEquals(header.y() + 9.0F, keybind.y(), DELTA);
		
		assertEquals(keybind.right() + 6.0F, bind.x(), DELTA);
		assertEquals(72.0F, bind.width(), DELTA);
		assertEquals(18.0F, bind.height(), DELTA);
		
		assertEquals(header.right() - 8.0F, visibility.right(), DELTA);
		assertEquals(72.0F, visibility.width(), DELTA);
		// 两个分段控件不能重叠
		assertTrue(bind.right() <= visibility.x());
	}
	
	@Test
	public void detailViewportStartsBelowTheHeader()
	{
		Rect detail = new Rect(400.0F, 200.0F, 300.0F, 300.0F);
		Rect viewport = EpsilonPanelLayout.detailViewport(detail);
		
		assertEquals(detail.y() + 34.0F + 36.0F + 6.0F, viewport.y(), DELTA);
		assertEquals(403.0F, viewport.x(), DELTA);
		assertEquals(detail.bottom() - viewport.y() - 10.0F, viewport.height(),
			DELTA);
	}
	
	@Test
	public void settingRowsUseTheirOwnPitch()
	{
		Rect viewport = new Rect(400.0F, 276.0F, 294.0F, 214.0F);
		
		assertEquals(93.0F, EpsilonPanelLayout.settingContentHeight(3), DELTA);
		assertEquals(276.0F,
			EpsilonPanelLayout.settingRow(viewport, 0, 0F, false).y(), DELTA);
		assertEquals(276.0F + 31.0F,
			EpsilonPanelLayout.settingRow(viewport, 1, 0F, false).y(), DELTA);
		assertEquals(28.0F,
			EpsilonPanelLayout.settingRow(viewport, 0, 0F, false).height(),
			DELTA);
	}
	
	@Test
	public void settingControlsAreTrailing()
	{
		Rect row = new Rect(400.0F, 300.0F, 294.0F, 28.0F);
		
		Rect track = EpsilonPanelLayout.settingSliderTrack(row);
		assertEquals(row.right() - 121.0F, track.x(), DELTA);
		assertEquals(72.0F, track.width(), DELTA);
		assertEquals(6.0F, track.height(), DELTA);
		
		Rect field = EpsilonPanelLayout.settingValueField(row);
		assertEquals(row.right() - 5.0F, field.right(), DELTA);
		assertEquals(40.0F, field.width(), DELTA);
		// 数值框在滑条右边，不重叠
		assertTrue(track.right() <= field.x());
		
		Rect toggle = EpsilonPanelLayout.settingSwitch(row);
		assertEquals(row.right() - 5.0F, toggle.right(), DELTA);
		assertEquals(row.y() + 6.0F, toggle.y(), DELTA);
		
		// chip 宽度随文字增长但有上限
		assertEquals(96.0F,
			EpsilonPanelLayout.settingChip(row, 500F).width(), DELTA);
		assertEquals(50.0F,
			EpsilonPanelLayout.settingChip(row, 24F).width(), DELTA);
		assertEquals(row.right() - 5.0F,
			EpsilonPanelLayout.settingChip(row, 24F).right(), DELTA);
	}
	
	@Test
	public void scrollThumbOnlyAppearsWhenNeeded()
	{
		Rect viewport = new Rect(100.0F, 200.0F, 160.0F, 200.0F);
		
		assertNull(EpsilonPanelLayout.scrollThumb(viewport, 200F, 0F));
		assertNull(EpsilonPanelLayout.scrollThumb(viewport, 120F, 0F));
		assertNotNull(EpsilonPanelLayout.scrollThumb(viewport, 400F, 0F));
	}
	
	@Test
	public void scrollThumbStaysInsideTheTrack()
	{
		Rect viewport = new Rect(100.0F, 200.0F, 160.0F, 200.0F);
		float content = 400.0F;
		float maxScroll = EpsilonPanelLayout.maxScroll(content, viewport);
		assertEquals(200.0F, maxScroll, DELTA);
		
		Rect top = EpsilonPanelLayout.scrollThumb(viewport, content, 0F);
		assertEquals(200.0F, top.y(), DELTA);
		assertEquals(viewport.right() - 2.5F, top.right(), DELTA);
		assertEquals(3.5F, top.width(), DELTA);
		// 高 = 视口/内容 * 轨道 = 100
		assertEquals(100.0F, top.height(), DELTA);
		
		Rect bottom =
			EpsilonPanelLayout.scrollThumb(viewport, content, maxScroll);
		assertEquals(viewport.bottom(), bottom.bottom(), DELTA);
		
		// 越界滚动被夹取
		Rect clamped =
			EpsilonPanelLayout.scrollThumb(viewport, content, maxScroll * 4F);
		assertEquals(viewport.bottom(), clamped.bottom(), DELTA);
	}
	
	@Test
	public void scrollThumbRespectsItsMinimumHeight()
	{
		Rect viewport = new Rect(100.0F, 200.0F, 160.0F, 200.0F);
		Rect thumb = EpsilonPanelLayout.scrollThumb(viewport, 100000F, 0F);
		
		assertEquals(10.0F, thumb.height(), DELTA);
	}
	
	@Test
	public void rectHelpersBehave()
	{
		Rect rect = new Rect(10.0F, 20.0F, 100.0F, 40.0F);
		
		assertEquals(110.0F, rect.right(), DELTA);
		assertEquals(60.0F, rect.bottom(), DELTA);
		assertEquals(60.0F, rect.centerX(), DELTA);
		assertEquals(40.0F, rect.centerY(), DELTA);
		assertTrue(rect.contains(10.0, 20.0));
		assertTrue(rect.contains(110.0, 60.0));
		assertFalse(rect.contains(9.9, 20.0));
		assertFalse(rect.contains(10.0, 60.1));
		
		Rect inset = rect.inset(5.0F);
		assertEquals(15.0F, inset.x(), DELTA);
		assertEquals(90.0F, inset.width(), DELTA);
		// 内缩过量不会得到负尺寸
		assertEquals(0.0F, rect.inset(500.0F).width(), DELTA);
		
		assertTrue(rect.intersectsVertically(new Rect(0F, 0F, 1F, 30F)));
		assertFalse(rect.intersectsVertically(new Rect(0F, 61F, 1F, 10F)));
	}
}
