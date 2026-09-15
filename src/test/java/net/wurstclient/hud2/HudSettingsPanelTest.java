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
