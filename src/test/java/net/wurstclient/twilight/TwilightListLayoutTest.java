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

import net.wurstclient.twilight.TwilightShellLayout.Frame;
import net.wurstclient.twilight.TwilightShellLayout.Rect;

/**
 * Tests the chart and list geometry.
 *
 * <p>
 * The chart of the home page is eight entries in two columns of four, the list
 * rows use the 64px pitch measured in the reference screenshot. The tests check
 * the grid invariants - rows never overlap, both columns are equal, the covers
 * and titles stay inside their row - for several canvas sizes.
 */
final class TwilightListLayoutTest
{
	private static final int WIDTH = 1500;
	private static final int HEIGHT = 880;
	
	@Test
	void theChartIsTwoColumnsOfFour()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Rect area = new Rect(240, 400, 1200, 400);
		Rect[] rows = TwilightListLayout.chartRows(frame, area);
		
		assertEquals(TwilightListLayout.CHART_LIMIT, rows.length);
		assertEquals(4, TwilightListLayout.chartColumn(frame, area, 0).length);
		assertEquals(4, TwilightListLayout.chartColumn(frame, area, 1).length);
	}
	
	@Test
	void theTwoChartColumnsAreEqualAndSeparated()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Rect area = new Rect(240, 400, 1200, 400);
		Rect[] left = TwilightListLayout.chartColumn(frame, area, 0);
		Rect[] right = TwilightListLayout.chartColumn(frame, area, 1);
		
		assertEquals(left[0].width(), right[0].width());
		assertEquals(left[0].right() + frame.px(TwilightListLayout.CHART_COLUMN_GAP),
			right[0].x());
		assertEquals(area.right(), right[0].right());
		assertFalse(left[0].intersects(right[0]));
	}
	
	@Test
	void chartRowsStackWithoutOverlap()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Rect area = new Rect(240, 400, 1200, 400);
		Rect[] rows = TwilightListLayout.chartRows(frame, area);
		
		for(int i = 1; i < rows.length; i++)
			assertFalse(rows[i - 1].intersects(rows[i]),
				rows[i - 1] + " vs " + rows[i]);
		
		// the first four are the left column, the next four the right one
		assertEquals(rows[0].x(), rows[3].x());
		assertEquals(rows[4].x(), rows[7].x());
		assertTrue(rows[4].x() > rows[3].x());
	}
	
	@Test
	void aChartRowKeepsItsCoverAndTitleInside()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Rect area = new Rect(240, 400, 1200, 400);
		Rect row = TwilightListLayout.chartRows(frame, area)[0];
		Rect cover = TwilightListLayout.rowCover(frame, row, true);
		Rect title = TwilightListLayout.rowTitle(frame, row, true);
		
		assertEquals(frame.px(TwilightListLayout.CHART_COVER), cover.width());
		assertEquals(row.centerY(), cover.centerY());
		assertTrue(cover.x() >= row.x());
		assertTrue(cover.bottom() <= row.bottom());
		assertEquals(cover.right() + frame.px(TwilightListLayout.TEXT_GAP),
			title.x());
		assertTrue(title.right() <= row.right());
		assertFalse(cover.intersects(title));
	}
	
	@Test
	void aListRowIsFullWidth()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Rect area = new Rect(240, 200, 1200, 300);
		Rect[] rows = TwilightListLayout.listRows(frame, area, 10);
		int pitch = frame.px(TwilightListLayout.ROW_HEIGHT);
		
		assertEquals(4, rows.length, "300px fits four 64px rows");
		assertEquals(area.x(), rows[0].x());
		assertEquals(area.right(), rows[0].right());
		assertEquals(pitch, rows[1].y() - rows[0].y());
	}
	
	@Test
	void anEmptyOrNegativeCountGivesNoRows()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Rect area = new Rect(240, 200, 1200, 300);
		
		assertEquals(0, TwilightListLayout.listRows(frame, area, 0).length);
		assertEquals(0, TwilightListLayout.listRows(frame, area, -3).length);
	}
	
	@Test
	void theIndexColumnSitsInFrontOfTheRow()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		Rect row = new Rect(240, 200, 600, frame.px(64));
		Rect index = TwilightListLayout.rowIndex(frame, row);
		Rect cover = TwilightListLayout.rowCover(frame, row, false);
		
		assertEquals(row.x() + frame.px(TwilightListLayout.ROW_PADDING),
			index.x());
		assertTrue(index.right() <= cover.x());
	}
	
	@Test
	void onlyTheVisibleRowsAreReturned()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		int pitch = frame.px(TwilightListLayout.ROW_HEIGHT);
		int[] first = TwilightListLayout.visibleRows(frame, 100, 0, pitch * 3);
		int[] scrolled =
			TwilightListLayout.visibleRows(frame, 100, pitch * 10, pitch * 3);
		
		assertEquals(4, first.length);
		assertEquals(0, first[0]);
		assertEquals(10, scrolled[0]);
		assertTrue(scrolled[scrolled.length - 1] <= 13);
	}
	
	@Test
	void theVisibleWindowIsClampedToTheList()
	{
		Frame frame = TwilightShellLayout.layout(WIDTH, HEIGHT);
		int pitch = frame.px(TwilightListLayout.ROW_HEIGHT);
		int[] visible =
			TwilightListLayout.visibleRows(frame, 5, pitch * 10, pitch * 3);
		
		assertTrue(visible.length <= 5);
		
		for(int index : visible)
			assertTrue(index >= 0 && index < 5);
	}
	
	@Test
	void theScrollOffsetIsClamped()
	{
		// five 64px rows in a 128px viewport: at most 320 - 128 = 192
		assertEquals(0, TwilightListLayout.clampScroll(-50, 5, 64, 128));
		assertEquals(100, TwilightListLayout.clampScroll(100, 5, 64, 128));
		assertEquals(192, TwilightListLayout.clampScroll(5000, 5, 64, 128));
	}
	
	@Test
	void rowsNeverOverlapForSeveralCanvasSizes()
	{
		for(int[] size : new int[][]{{1500, 880}, {1100, 700}, {800, 600}})
		{
			Frame frame = TwilightShellLayout.layout(size[0], size[1]);
			Rect area = new Rect(frame.contentBody.x() + 24,
				frame.contentBody.y(), frame.contentBody.width() - 48, 300);
			Rect[] rows = TwilightListLayout.listRows(frame, area, 6);
			
			for(int i = 1; i < rows.length; i++)
				assertFalse(rows[i - 1].intersects(rows[i]));
		}
	}
}
