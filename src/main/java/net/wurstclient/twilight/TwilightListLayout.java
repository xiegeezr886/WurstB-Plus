/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import net.wurstclient.twilight.TwilightShellLayout.Frame;
import net.wurstclient.twilight.TwilightShellLayout.Rect;

/**
 * The list geometry of the reference: the two column chart of the home page and
 * the plain song list of the library pages.
 *
 * <p>
 * The reference row height is not in the snapshot (its {@code SongList.vue} was
 * not part of the checkout), so the 64px row pitch measured in
 * {@code local-dashboard.png} is used, together with the column layout of the
 * chart section. Values taken from {@code StreamingHome.vue} are marked in the
 * constants below.
 *
 * <p>
 * Deliberately free of Minecraft types so that the layout can be unit tested.
 */
public final class TwilightListLayout
{
	/** {@code CHART_LIMIT = 8}, i.e. four rows in two columns. */
	public static final int CHART_LIMIT = 8;
	public static final int CHART_COLUMNS = 2;
	public static final int CHART_ROWS = CHART_LIMIT / CHART_COLUMNS;
	
	/** Measured in the reference screenshot. */
	public static final int ROW_HEIGHT = 64;
	
	/** The gap between the two chart columns, {@code .chart { gap }}. */
	public static final int CHART_COLUMN_GAP = 24;
	
	/** The cover of a row: 46px in the chart, 40px in a dense list. */
	public static final int CHART_COVER = 46;
	public static final int LIST_COVER = 40;
	public static final int COVER_RADIUS = 10;
	
	/** Text insets inside a row. */
	public static final int ROW_PADDING = 10;
	public static final int TEXT_GAP = 12;
	
	private TwilightListLayout()
	{
		
	}
	
	/**
	 * The rows of one column of the chart.
	 */
	public static Rect[] chartColumn(Frame frame, Rect area, int column)
	{
		if(column < 0 || column >= CHART_COLUMNS)
			return new Rect[0];
		
		int gap = frame.px(CHART_COLUMN_GAP);
		int width = Math.max(0, (area.width() - gap) / CHART_COLUMNS);
		int x = area.x() + column * (width + gap);
		int height = frame.px(ROW_HEIGHT);
		Rect[] rows = new Rect[CHART_ROWS];
		
		for(int row = 0; row < CHART_ROWS; row++)
			rows[row] = new Rect(x, area.y() + row * height, width, height);
		
		return rows;
	}
	
	/**
	 * All chart rows in reading order: left column first, then the right one, as
	 * the reference fills them.
	 */
	public static Rect[] chartRows(Frame frame, Rect area)
	{
		Rect[] left = chartColumn(frame, area, 0);
		Rect[] right = chartColumn(frame, area, 1);
		Rect[] all = new Rect[left.length + right.length];
		
		System.arraycopy(left, 0, all, 0, left.length);
		System.arraycopy(right, 0, all, left.length, right.length);
		return all;
	}
	
	/** The height the chart needs for its rows. */
	public static int chartHeight(Frame frame)
	{
		return frame.px(ROW_HEIGHT) * CHART_ROWS;
	}
	
	/**
	 * A song list: one full width row per entry, the cover on the left.
	 */
	public static Rect[] listRows(Frame frame, Rect area, int count)
	{
		if(count <= 0 || area.width() <= 0)
			return new Rect[0];
		
		int height = frame.px(ROW_HEIGHT);
		int fits = Math.max(0, Math.min(count, area.height() / height));
		Rect[] rows = new Rect[fits];
		
		for(int row = 0; row < fits; row++)
			rows[row] = new Rect(area.x(), area.y() + row * height, area.width(),
				height);
		
		return rows;
	}
	
	/** Width of the leading index / playing indicator column. */
	public static final int INDEX_WIDTH = 18;
	
	/**
	 * The cover box of a row, vertically centred.
	 *
	 * <p>
	 * A chart row starts right after the padding; a list row leaves the
	 * {@link #INDEX_WIDTH} leading slot free for the index column the reference
	 * draws in front of it.
	 */
	public static Rect rowCover(Frame frame, Rect row, boolean chart)
	{
		int size = frame.px(chart ? CHART_COVER : LIST_COVER);
		int inset = ROW_PADDING + (chart ? 0 : INDEX_WIDTH);
		return new Rect(row.x() + frame.px(inset), row.centerY() - size / 2,
			size, size);
	}
	
	/**
	 * The title of a row, left aligned next to its cover.
	 */
	public static Rect rowTitle(Frame frame, Rect row, boolean chart)
	{
		Rect cover = rowCover(frame, row, chart);
		return new Rect(cover.right() + frame.px(TEXT_GAP), row.y(),
			Math.max(0, row.right() - cover.right() - frame.px(TEXT_GAP)
				- frame.px(ROW_PADDING)),
			row.height());
	}
	
	/**
	 * The playing indicator / index column that the reference puts in front of a
	 * list row.
	 */
	public static Rect rowIndex(Frame frame, Rect row)
	{
		return new Rect(row.x() + frame.px(ROW_PADDING), row.y(),
			frame.px(INDEX_WIDTH), row.height());
	}
	
	/**
	 * @return the row under the given point, or -1.
	 */
	public static int rowAt(Frame frame, Rect[] rows, double x, double y)
	{
		for(int i = 0; i < rows.length; i++)
			if(rows[i] != null && rows[i].contains(x, y))
				return i;
		
		return -1;
	}
	
	/**
	 * Which of these rows are visible in the viewport, i.e. the windowing the
	 * reference does with an {@code IntersectionObserver}. The list is assumed to
	 * be scrolled by {@code scrollOffset} pixels.
	 */
	public static int[] visibleRows(Frame frame, int count, int scrollOffset,
		int viewportHeight)
	{
		int height = frame.px(ROW_HEIGHT);
		
		if(height <= 0 || count <= 0 || viewportHeight <= 0)
			return new int[0];
		
		int first = Math.max(0, scrollOffset / height);
		int last = Math.min(count - 1,
			(scrollOffset + viewportHeight) / height);
		int[] visible = new int[Math.max(0, last - first + 1)];
		
		for(int i = 0; i < visible.length; i++)
			visible[i] = first + i;
		
		return visible;
	}
	
	/** The scroll offset is clamped to what the content allows. */
	public static int clampScroll(int scrollOffset, int count, int rowHeight,
		int viewportHeight)
	{
		int content = count * rowHeight;
		int max = Math.max(0, content - viewportHeight);
		return Math.max(0, Math.min(scrollOffset, max));
	}
}
