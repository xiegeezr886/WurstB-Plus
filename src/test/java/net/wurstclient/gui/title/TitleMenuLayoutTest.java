/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gui.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.wurstclient.gui.title.TitleMenuLayout.Rect;

/**
 * Tests the title menu geometry.
 *
 * <p>
 * The point of the layout is that it reproduces the reference screenshot at any
 * resolution, so the first test replays the measured pixels of that screenshot:
 * the reference frame is 1296x672 physical pixels, which is GUI scale 2 on a
 * 648x336 logical canvas, so every logical value doubled has to land on the
 * number measured in the picture.
 */
final class TitleMenuLayoutTest
{
	/** 参考截图 1296x672 物理像素 = GUI scale 2 下的逻辑画布。 */
	private static final int REF_WIDTH = 648;
	private static final int REF_HEIGHT = 336;
	/** 参考图里账号名那行的宽度。 */
	private static final int CHIP_TEXT = 60;
	private static final int TOLERANCE = 4;

	private static TitleMenuLayout reference()
	{
		return new TitleMenuLayout(REF_WIDTH, REF_HEIGHT, CHIP_TEXT);
	}

	/** 逻辑值 ×2 应当落在参考图量到的物理值上。 */
	private static void assertPhysical(String what, int logical, int measured)
	{
		assertEquals(measured, logical * 2, TOLERANCE, what);
	}

	private static List<Rect> allRects(TitleMenuLayout layout)
	{
		List<Rect> rects = new ArrayList<>();
		rects.add(layout.chip());
		rects.add(layout.avatar());
		rects.add(layout.gear());
		rects.add(layout.logo());
		rects.add(layout.rail());
		rects.add(layout.menu());
		rects.addAll(layout.actions());
		rects.addAll(layout.menuRows());
		return rects;
	}

	/**
	 * 参考图上量到的像素：胶囊 16,15 起、218x70；齿轮右边距 16、36x36；动作条
	 * x 20..920、y 579..646，里面 4 个 208x48 的按钮、间隔 16；logo 的
	 * y 506..547。
	 */
	@Test
	void theReferenceFrameReproducesTheMeasuredScreenshot()
	{
		TitleMenuLayout layout = reference();

		assertPhysical("rail left", layout.rail().x(), 20);
		assertPhysical("rail right", layout.rail().right(), 920);
		assertPhysical("rail top", layout.rail().y(), 579);
		assertPhysical("rail bottom", layout.rail().bottom(), 646);

		assertPhysical("chip left", layout.chip().x(), 16);
		assertPhysical("chip top", layout.chip().y(), 15);
		assertPhysical("chip height", layout.chip().height(), 70);
		assertPhysical("avatar size", layout.avatar().width(), 42);

		assertPhysical("gear size", layout.gear().width(), 36);
		assertPhysical("gear right", layout.gear().right(), 1280);

		assertPhysical("action width", layout.action(0).width(), 208);
		assertPhysical("action height", layout.action(0).height(), 48);
		assertPhysical("action gap",
			layout.action(1).x() - layout.action(0).right(), 16);

		assertPhysical("logo left", layout.logo().x(), 28);
		assertPhysical("logo bottom", layout.logo().bottom(), 547);
	}

	@Test
	void theFourActionsSplitTheRailEvenly()
	{
		TitleMenuLayout layout = reference();

		assertEquals(TitleMenuLayout.ACTION_COUNT, layout.actions().size());

		int width = layout.action(0).width();
		int gap = layout.action(1).x() - layout.action(0).right();

		for(int i = 0; i < TitleMenuLayout.ACTION_COUNT; i++)
		{
			Rect action = layout.action(i);
			assertEquals(width, action.width(), "action " + i + " width");
			assertEquals(layout.action(0).x() + i * (width + gap), action.x(),
				"action " + i + " x");
			assertTrue(action.y() > layout.rail().y(), "action inside rail");
			assertTrue(action.bottom() < layout.rail().bottom(),
				"action inside rail");
		}

		// 左右内边距相等（整数除法最多差几像素）
		int left = layout.action(0).x() - layout.rail().x();
		int right = layout.rail().right()
			- layout.action(TitleMenuLayout.ACTION_COUNT - 1).right();
		assertTrue(Math.abs(left - right) <= TOLERANCE, "rail padding");
	}

	@Test
	void theRailHangsOnTheBottomMargin()
	{
		for(int[] canvas : new int[][]{{648, 336}, {960, 540}, {1920, 1080},
			{320, 240}})
		{
			TitleMenuLayout layout =
				new TitleMenuLayout(canvas[0], canvas[1], CHIP_TEXT);
			int bottomMargin = canvas[1] - layout.rail().bottom();

			assertTrue(bottomMargin
				>= TitleMenuLayout.BOTTOM_MARGIN_MIN - 1, "bottom margin");
			assertTrue(bottomMargin
				<= TitleMenuLayout.BOTTOM_MARGIN_MAX + 1, "bottom margin");
		}
	}

	@Test
	void theChipAndTheGearShareTheTopMargin()
	{
		TitleMenuLayout layout = reference();

		assertEquals(layout.chip().y(), layout.gear().y());
		assertEquals(layout.margin(), layout.chip().x());
		assertEquals(layout.width() - layout.margin(), layout.gear().right());
		assertTrue(layout.gear().width() <= layout.chip().height(),
			"the gear never grows past the chip");
	}

	@Test
	void theChipGrowsWithTheNameAndStopsAtTheMaximumWidth()
	{
		TitleMenuLayout narrow = new TitleMenuLayout(REF_WIDTH, REF_HEIGHT, 20);
		TitleMenuLayout wide = new TitleMenuLayout(REF_WIDTH, REF_HEIGHT, 1000);

		assertTrue(wide.chip().width() > narrow.chip().width());
		assertTrue(wide.chip().width() <= Math
			.round(REF_WIDTH * TitleMenuLayout.CHIP_WIDTH_MAX_RATIO) + 1);
		assertTrue(wide.avatar().right() < wide.chip().right(),
			"the avatar stays inside the chip");
	}

	@Test
	void theMenuHangsUnderTheGearAndAboveTheRail()
	{
		TitleMenuLayout layout = reference();

		assertEquals(layout.gear().right(), layout.menu().right());
		assertTrue(layout.menu().y() >= layout.gear().bottom());
		assertFalse(layout.menu().intersects(layout.rail()));

		List<Rect> rows = layout.menuRows();
		assertEquals(TitleMenuLayout.MENU_COUNT, rows.size());

		for(int i = 0; i < rows.size(); i++)
		{
			Rect row = rows.get(i);
			assertTrue(row.x() >= layout.menu().x(), "row inside menu");
			assertTrue(row.right() <= layout.menu().right(),
				"row inside menu");
			assertTrue(row.y() >= layout.menu().y(), "row inside menu");
			assertTrue(row.bottom() <= layout.menu().bottom(),
				"row inside menu");

			if(i > 0)
				assertEquals(rows.get(i - 1).bottom(), row.y(),
					"rows are stacked without gaps");
		}
	}

	@Test
	void theRegionsNeverOverlapOnAUsableScreen()
	{
		int[][] canvases = {{256, 192}, {320, 240}, {480, 270}, {648, 336},
			{800, 600}, {960, 540}, {1280, 720}, {1920, 1080}, {2560, 1440}};

		for(int[] canvas : canvases)
		{
			int width = canvas[0];
			int height = canvas[1];
			TitleMenuLayout layout = new TitleMenuLayout(width, height, 90);

			for(Rect rect : allRects(layout))
			{
				assertTrue(rect.x() >= 0, width + "x" + height + " left edge");
				assertTrue(rect.y() >= 0, width + "x" + height + " top edge");
				assertTrue(rect.right() <= width,
					width + "x" + height + " right edge");
				assertTrue(rect.bottom() <= height,
					width + "x" + height + " bottom edge");
			}

			assertFalse(layout.chip().intersects(layout.rail()),
				width + "x" + height + " chip over rail");
			assertFalse(layout.chip().intersects(layout.logo()),
				width + "x" + height + " chip over logo");
			assertFalse(layout.logo().intersects(layout.rail()),
				width + "x" + height + " logo over rail");
			assertFalse(layout.chip().intersects(layout.gear()),
				width + "x" + height + " chip over gear");
		}
	}

	/**
	 * 比例式的布局在「钳制还没生效」的区间里应当等比缩放；超过上限之后就不再
	 * 跟着屏幕长大，这是故意的（{@link TitleMenuLayout#CHIP_HEIGHT_MAX}）。
	 */
	@Test
	void theLayoutScalesWithTheCanvas()
	{
		TitleMenuLayout small = new TitleMenuLayout(540, 280, CHIP_TEXT);
		TitleMenuLayout large = new TitleMenuLayout(648, 336, CHIP_TEXT);
		float scale = 648F / 540F;

		assertEquals(scale, large.rail().height() / (float)small.rail().height(),
			0.05F, "rail height");
		assertEquals(scale,
			large.action(0).width() / (float)small.action(0).width(), 0.05F,
			"action width");
		assertEquals(scale, large.gear().width() / (float)small.gear().width(),
			0.05F, "gear size");
		assertEquals(scale, large.rail().width() / (float)small.rail().width(),
			0.05F, "rail width");
	}

	@Test
	void degenerateCanvasesStillProduceUsableRectangles()
	{
		for(int[] canvas : new int[][]{{1, 1}, {8, 8}, {64, 48}, {100, 80}})
		{
			TitleMenuLayout layout =
				new TitleMenuLayout(canvas[0], canvas[1], 90);

			for(Rect rect : allRects(layout))
			{
				assertTrue(rect.width() >= 1,
					canvas[0] + "x" + canvas[1] + " width");
				assertTrue(rect.height() >= 1,
					canvas[0] + "x" + canvas[1] + " height");
			}
		}
	}

	@Test
	void textIsCentredInItsBox()
	{
		assertEquals(8, TitleMenuLayout.centerTextY(0, 24));
		assertEquals(1, TitleMenuLayout.centerTextY(0, 9));
		assertEquals(102, TitleMenuLayout.centerTextY(100, 11));	}
}
