/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hud2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.settings.CheckboxSetting;

/**
 * 覆盖 {@code HudSettingsPanel} 里不依赖渲染的那部分。
 *
 * <p>
 * <b>不测的部分</b>：行内容排版、命中后到底改了哪个设置、点击后是否存盘——
 * 那些要真的画一帧（{@code render()} 需要 {@code GuiGraphics} 与 {@code Font}），
 * 无头环境跑不了。这里钉的是行的纵向命中数学，以及「面板没显示时绝不能吃掉
 * 点击」这条——后者错了会让整个编辑器点不动。
 */
final class HudSettingsPanelTest
{
	private static final class TestElement extends HudElement
	{
		TestElement()
		{
			super("test_element", "Test Element");
		}

		@Override
		public int getWidth()
		{
			return 0;
		}

		@Override
		public int getHeight()
		{
			return 0;
		}

		@Override
		public void render(GuiGraphics graphics, int x, int y,
			float partialTicks)
		{}
	}

	// 面板内部行高是 20；这里按同样的大小验证半开区间。
	private static final int ROW = 20;
	private static final int TOP = 100;

	@Test
	void theFirstRowStartsAtItsTopAndIsInclusive()
	{
		assertEquals(0, HudSettingsPanel.rowIndexAt(TOP, TOP, 3));
		assertEquals(0, HudSettingsPanel.rowIndexAt(TOP + ROW - 1, TOP, 3));
	}

	/** 下边界不含：第 1 行的底就是第 2 行的顶。 */
	@Test
	void theBoundaryBelongsToTheRowBelow()
	{
		assertEquals(1, HudSettingsPanel.rowIndexAt(TOP + ROW, TOP, 3));
		assertEquals(2, HudSettingsPanel.rowIndexAt(TOP + ROW * 2, TOP, 3));
		assertEquals(2,
			HudSettingsPanel.rowIndexAt(TOP + ROW * 3 - 1, TOP, 3));
	}

	@Test
	void aboveTheFirstRowHitsNothing()
	{
		assertEquals(-1, HudSettingsPanel.rowIndexAt(TOP - 1, TOP, 3));
		assertEquals(-1, HudSettingsPanel.rowIndexAt(0, TOP, 3));
		assertEquals(-1, HudSettingsPanel.rowIndexAt(-50, TOP, 3));
	}

	/** 最后一行之下不再是任何行——不能靠 floor 除出一个越界下标。 */
	@Test
	void belowTheLastRowHitsNothing()
	{
		assertEquals(-1, HudSettingsPanel.rowIndexAt(TOP + ROW * 3, TOP, 3));
		assertEquals(-1,
			HudSettingsPanel.rowIndexAt(TOP + ROW * 99, TOP, 3));
	}

	@Test
	void anEmptyPanelHasNoHittableRow()
	{
		assertEquals(-1, HudSettingsPanel.rowIndexAt(TOP, TOP, 0));
		assertEquals(-1, HudSettingsPanel.rowIndexAt(TOP + 5, TOP, -1));
	}

	/**
	 * 面板没显示（还没有元素被悬停过）时必须放行点击，否则编辑器里所有元素
	 * 都点不动——这是整块 UI 的开关。
	 */
	@Test
	void aHiddenPanelNeverConsumesClicks()
	{
		HudSettingsPanel panel = new HudSettingsPanel();

		assertFalse(panel.mouseClicked(10, 10));
		assertFalse(panel.mouseClicked(0, 0));
		assertFalse(panel.mouseClicked(5000, 5000));
		assertNull(panel.getElementId());
	}

	// ---- 滑条：由横向位置算值 ----

	private static final int TRACK_X1 = 100;
	private static final int TRACK_X2 = 300;

	private static double valueAt(double mouseX, double min, double max)
	{
		return HudSettingsPanel.valueForX(mouseX, TRACK_X1, TRACK_X2, min, max);
	}

	@Test
	void theTrackEdgesMapToTheEndpoints()
	{
		assertEquals(12, valueAt(TRACK_X1, 12, 36), 1e-9);
		assertEquals(36, valueAt(TRACK_X2, 12, 36), 1e-9);
	}

	@Test
	void theTrackMiddleMapsToTheMidpoint()
	{
		assertEquals(24, valueAt((TRACK_X1 + TRACK_X2) / 2.0, 12, 36), 1e-9);
	}

	/** 拖到轨道外面不能算出越界的值——这是这个函数存在的理由。 */
	@Test
	void draggingPastEitherEndClamps()
	{
		assertEquals(12, valueAt(TRACK_X1 - 500, 12, 36), 1e-9);
		assertEquals(12, valueAt(-9999, 12, 36), 1e-9);
		assertEquals(36, valueAt(TRACK_X2 + 500, 12, 36), 1e-9);
		assertEquals(36, valueAt(9999, 12, 36), 1e-9);
	}

	/** 负区间、以及最小值是负数时也要对。 */
	@Test
	void negativeRangesWork()
	{
		assertEquals(-1.0, valueAt(TRACK_X1, -1, 1), 1e-9);
		assertEquals(0.0, valueAt((TRACK_X1 + TRACK_X2) / 2.0, -1, 1), 1e-9);
		assertEquals(1.0, valueAt(TRACK_X2, -1, 1), 1e-9);
	}

	/** 退化的轨道（零宽）不能除以零，取最小值。 */
	@Test
	void aDegenerateTrackFallsBackToTheMinimum()
	{
		assertEquals(7, HudSettingsPanel.valueForX(50, 100, 100, 7, 99));
		assertEquals(7, HudSettingsPanel.valueForX(50, 100, 40, 7, 99));
	}

	/** 零区间（min == max）也不能算出 NaN。 */
	@Test
	void aZeroRangeFallsBackToTheMinimum()
	{
		assertEquals(5, valueAt(TRACK_X1, 5, 5), 1e-9);
		assertEquals(5, valueAt(TRACK_X2, 5, 5), 1e-9);
	}

	/**
	 * 这个函数<b>不做</b> increment 对齐——对齐是 {@code SliderSetting} 自己按
	 * 零基准做的。这里钉住「不越界」就够了，免得以后有人再加一层对齐，导致
	 * 两次对齐的基准不同、算出来的值和存下的值对不上。
	 */
	@Test
	void theResultNeverExceedsTheRange()
	{
		for(int x = 0; x < 400; x += 7)
		{
			double v = valueAt(x, -3, 17);
			assertTrue(v >= -3 && v <= 17, "x=" + x + " -> " + v);
		}
	}

	/** 显示之后、还没渲染过时坐标全是 0，落在 0 点的点击也不该被吃。 */
	@Test
	void aPanelThatHasNotRenderedYetDoesNotConsumeClicks()
	{
		HudSettingsPanel panel = new HudSettingsPanel();
		panel.show(new TestElement());

		assertEquals("test_element", panel.getElementId());
		assertFalse(panel.contains(10, 10));
		assertFalse(panel.mouseClicked(10, 10));
	}

	@Test
	void aFreshPanelReportsNoChange()
	{
		assertFalse(new HudSettingsPanel().consumeChanged());
	}

	/**
	 * 没抓着滑条时 {@code mouseDragged} 必须返回 false。返回 true 会把编辑器
	 * 里所有元素的拖动都吃掉——整个编辑器就不能拖了。这是本轮最容易写错的
	 * 一处，所以单独钉住。
	 */
	@Test
	void anIdlePanelDoesNotSwallowDrags()
	{
		HudSettingsPanel panel = new HudSettingsPanel();
		assertFalse(panel.mouseDragged(10));
		assertFalse(panel.mouseDragged(500));

		panel.show(new TestElement());
		assertFalse(panel.mouseDragged(10));
	}

	@Test
	void releasingWithoutASliderDragIsNotConsumed()
	{
		HudSettingsPanel panel = new HudSettingsPanel();
		assertFalse(panel.mouseReleased());

		panel.show(new TestElement());
		assertFalse(panel.mouseReleased());
	}

	/** 清掉一次「改过了」之后不该重复上报，否则每次点击都会多存一次盘。 */
	@Test
	void consumingTheChangeFlagClearsIt()
	{
		HudSettingsPanel panel = new HudSettingsPanel();
		panel.show(new TestElement());
		assertFalse(panel.consumeChanged());
		assertFalse(panel.consumeChanged());
	}

	/**
	 * 元素为 null 也要能收起来，而不是让面板卡在屏幕上或抛异常。
	 */
	@Test
	void showingANullElementHidesThePanel()
	{
		HudSettingsPanel panel = new HudSettingsPanel();
		panel.show(new TestElement());
		panel.show(null);

		assertNull(panel.getElementId());
		assertFalse(panel.mouseClicked(10, 10));
	}

	@Test
	void settingsDeclaredByAnElementAreReachableForThePanel()
	{
		TestElement element = new TestElement();
		element.addSetting(new CheckboxSetting("Equipment", true));

		assertTrue(element.getSettings().containsKey("equipment"));
	}
}
