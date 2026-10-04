package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.twilight.TwilightShellLayout.Frame;
import net.wurstclient.twilight.TwilightShellLayout.Rect;

/**
 * 校验歌单行的栅格。
 *
 * <p>
 * 参考是 {@code .chart-row { grid-template-columns: 34px 48px minmax(0,1fr) auto }}：
 * 序号 34、封面 48、文字占剩余、时长靠右。这些值曾经是 18 / 46 / 无时长列，
 * 行里既没有序号也没有时长。
 */
final class TwilightListRowLayoutTest
{
	private static Frame frame()
	{
		return TwilightShellLayout.layout(1500, 880);
	}

	@Test
	void theGridFollowsTheReferenceColumns()
	{
		Frame frame = frame();
		Rect row = new Rect(240, 200, 628, frame.px(66));

		Rect index = TwilightListLayout.rowIndex(frame, row);
		Rect cover = TwilightListLayout.rowCover(frame, row, true);
		Rect title = TwilightListLayout.rowTitle(frame, row, true);
		Rect duration = TwilightListLayout.rowDuration(frame, row);

		// 序号 -> 封面 -> 文字 -> 时长，从左到右且不重叠
		assertTrue(index.right() <= cover.x(), "序号列应在封面之前");
		assertTrue(cover.right() <= title.x(), "封面应在文字之前");
		assertTrue(title.right() <= duration.x(), "文字应在时长之前");
		assertTrue(duration.right() <= row.right(), "时长不应越过行右边界");

		assertEquals(frame.px(TwilightListLayout.INDEX_WIDTH), index.width());
		assertEquals(frame.px(TwilightListLayout.CHART_COVER), cover.width());
	}

	@Test
	void theDurationHugsTheRightPadding()
	{
		Frame frame = frame();
		Rect row = new Rect(240, 200, 628, frame.px(66));
		Rect duration = TwilightListLayout.rowDuration(frame, row);

		assertEquals(row.right() - frame.px(TwilightListLayout.ROW_PADDING_X),
			duration.right());
	}

	/** 行高要装得下 48px 封面加上下 10px 内边距（参考的 .chart-row）。 */
	@Test
	void theRowIsTallEnoughForTheCover()
	{
		Frame frame = frame();
		int needed = frame.px(TwilightListLayout.CHART_COVER
			+ TwilightListLayout.CHART_PADDING_Y * 2);

		assertEquals(needed, frame.px(TwilightListLayout.ROW_HEIGHT),
			"行高应与封面加内边距一致");
	}
}
