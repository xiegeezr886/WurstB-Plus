package net.wurstclient.util.esp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.wurstclient.util.esp.EspNameTagElement.Icon;
import net.wurstclient.util.esp.EspNameTagElement.IconPosition;
import net.wurstclient.util.esp.EspNameTagLayout.GlyphMeasurer;
import net.wurstclient.util.esp.EspNameTagLayout.Layout;
import net.wurstclient.util.esp.EspNameTagLayout.Placed;

final class EspNameTagLayoutTest
{
	/** 每个字符宽 1，字号与字体都被排除在外，断言只看几何。 */
	private static final GlyphMeasurer ONE_PER_CHAR = text -> text.length();

	private static final float CENTER = 100;
	private static final float BOX_TOP = 50;

	@Test
	void emptyListHasNoWidthAndNoGapOverflow()
	{
		Layout layout = layout(List.of());

		assertEquals(0, layout.totalWidth());
		assertEquals(CENTER, layout.startX());
		assertTrue(layout.isEmpty());
	}

	@Test
	void singleTextElementIsCentredOnTheBox()
	{
		Layout layout = layout(List.of(element("abcd")));

		assertEquals(4, layout.totalWidth());
		assertEquals(CENTER - 2, layout.startX());
		assertEquals(1, layout.placed().size());
	}

	@Test
	void gapAppearsOnlyBetweenElements()
	{
		Layout layout = layout(
			List.of(element("abcd"), element("abcd"), element("abcd")));

		// 3 个宽 4 的元素 + 2 个间隔
		assertEquals(12 + 10, layout.totalWidth());

		List<Placed> placed = layout.placed();
		assertEquals(layout.startX(), placed.get(0).x());
		assertEquals(layout.startX() + 9, placed.get(1).x());
		assertEquals(layout.startX() + 18, placed.get(2).x());
	}

	@Test
	void rightIconSitsAfterTheText()
	{
		Placed placed = layout(List.of(
			new EspNameTagElement(new Icon("X", IconPosition.RIGHT, 0.5F),
				"ab", 0xFFFFFFFF)))
					.placed().get(0);

		assertEquals(placed.textX() + 2 + 0.5F, placed.iconX());
		assertEquals(2, placed.textWidth());
		assertEquals(1, placed.iconWidth());
	}

	@Test
	void leftIconShiftsTheTextRightAndLeadsTheElement()
	{
		Placed placed = layout(List.of(
			new EspNameTagElement(new Icon("X", IconPosition.LEFT, 0.5F),
				"ab", 0xFFFFFFFF)))
					.placed().get(0);

		assertEquals(placed.x() + 0.5F, placed.iconX());
		assertEquals(placed.x() + 1, placed.textX());
	}

	@Test
	void iconWidthCountsTowardsTheTotalWidth()
	{
		assertEquals(3, layout(List.of(
			new EspNameTagElement(new Icon("X"), "ab", 0xFFFFFFFF))).totalWidth());
	}

	@Test
	void iconOnlyElementHasNoTextWidth()
	{
		Placed placed =
			layout(List.of(new EspNameTagElement(new Icon("X"), 0xFFAAAAAA)))
				.placed().get(0);

		assertTrue(placed.hasIcon());
		assertEquals(0, placed.textWidth());
		assertEquals(1, placed.iconWidth());
		assertFalse(placed.element().hasText());
	}

	@Test
	void backgroundWrapsContentWithReferencePadding()
	{
		Placed placed = layout(List.of(
			new EspNameTagElement(new Icon("X"), "ab", 0xFFFFFFFF)))
				.placed().get(0);

		// 参考：宽 = 文本宽 + 图标宽 + 2 * 2，高 = 字号 + 2 * 2
		assertEquals(3 + 4, placed.bgWidth());
		assertEquals(9, placed.bgHeight());
		assertEquals(placed.x() - 2, placed.bgX());
	}

	@Test
	void backgroundSitsOneBaselineOffsetAboveTheBaseline()
	{
		Layout layout = layout(List.of(element("ab")));
		Placed placed = layout.placed().get(0);

		assertEquals(BOX_TOP - 4.5F, layout.baselineY());
		assertEquals(layout.baselineY() - 2 - 4.5F, placed.bgY());
	}

	@Test
	void emptyTextIsTreatedAsAbsent()
	{
		Placed placed = layout(List.of(new EspNameTagElement("", 0xFFFFFFFF)))
			.placed().get(0);

		assertFalse(placed.element().hasText());
		assertEquals(0, placed.textWidth());
	}

	// ------------------------------------------------------------------
	// 原版兜底路径的口径换算
	// ------------------------------------------------------------------

	@Test
	void vanillaScaleBringsTheVanillaLineDownToTheNameTagSize()
	{
		// 原版默认行高 9，铭牌字号 5
		assertEquals(5F / 9F, EspNameTagLayout.vanillaScale(9), 1E-5F);
		assertEquals(1F, EspNameTagLayout.vanillaScale(5), 1E-5F);
	}

	/** 行高非正时不能除零——字体对象异常或第三方字体都可能给 0。 */
	@Test
	void vanillaScaleIsSafeForANonPositiveLineHeight()
	{
		assertEquals(1F, EspNameTagLayout.vanillaScale(0), 1E-5F);
		assertEquals(1F, EspNameTagLayout.vanillaScale(-3), 1E-5F);
	}

	/**
	 * 原版 {@code drawString} 的 y 是顶边、Skia 的是基线，所以兜底路径要换算。
	 * 这里不去猜 ascent，而是把文字在背景条里竖直居中。
	 */
	@Test
	void vanillaTextTopCentresTheLineInsideTheBackground()
	{
		float bgY = 100;
		float bgHeight =
			EspNameTagLayout.FONT_SIZE + EspNameTagLayout.BG_PADDING * 2F;
		int lineHeight = 9;
		float scale = EspNameTagLayout.vanillaScale(lineHeight);

		float top = EspNameTagLayout.vanillaTextTop(bgY, bgHeight, scale,
			lineHeight);
		float bottom = top + lineHeight * scale;

		assertEquals(bgY + bgHeight / 2F, (top + bottom) / 2F, 1E-4F);
		assertTrue(top >= bgY, "text must not start above the background");
		assertTrue(bottom <= bgY + bgHeight,
			"text must not spill below the background");
	}

	@Test
	void vanillaTextTopFollowsTheBackgroundVertically()
	{
		float scale = EspNameTagLayout.vanillaScale(9);

		assertEquals(EspNameTagLayout.vanillaTextTop(100, 9, scale, 9) + 40,
			EspNameTagLayout.vanillaTextTop(140, 9, scale, 9), 1E-4F);
	}

	private static EspNameTagElement element(String text)
	{
		return new EspNameTagElement(text, 0xFFFFFFFF);
	}

	private static Layout layout(List<EspNameTagElement> elements)
	{
		return EspNameTagLayout.layout(elements, CENTER, BOX_TOP, ONE_PER_CHAR);
	}
}
