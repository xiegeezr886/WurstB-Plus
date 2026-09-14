package net.wurstclient.util.esp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import net.wurstclient.render.skia.EspIndicatorGlyphs;
import net.wurstclient.util.esp.EspNameTagPolicy.Options;
import net.wurstclient.util.esp.EspNameTagPolicy.State;

final class EspNameTagPolicyTest
{
	private static final Options ALL = Options.defaults();

	@Test
	void matchesTheReferenceElementOrder()
	{
		List<EspNameTagElement> elements = EspNameTagPolicy.build(ALL,
			new State(true, true, true, 12, "Steve", 20, 4));

		assertEquals(7, elements.size());

		// 指示：潜行 → 隐身 → 举盾
		assertEquals(EspIndicatorGlyphs.SNEAKING, glyph(elements.get(0)));
		assertEquals(EspNameTagPolicy.SNEAKING_COLOR, elements.get(0).color());
		assertEquals(EspIndicatorGlyphs.INVISIBLE, glyph(elements.get(1)));
		assertEquals(EspNameTagPolicy.INVISIBLE_COLOR, elements.get(1).color());
		assertEquals(EspIndicatorGlyphs.BLOCKING, glyph(elements.get(2)));
		assertEquals(EspNameTagPolicy.BLOCKING_COLOR, elements.get(2).color());

		// 距离 → 名字 → 血量 → 伤害吸收
		assertEquals("12m", elements.get(3).text());
		assertEquals(EspNameTagPolicy.DISTANCE_COLOR, elements.get(3).color());
		assertEquals("Steve", elements.get(4).text());
		assertEquals(EspNameTagPolicy.DEFAULT_COLOR, elements.get(4).color());
		// 血量与伤害吸收是「图标 + 文本」两个字段都有的元素
		assertEquals(EspIndicatorGlyphs.HEALTH,
			elements.get(5).icon().glyph());
		assertEquals("20", elements.get(5).text());
		assertEquals(EspIndicatorGlyphs.ABSORPTION,
			elements.get(6).icon().glyph());
		assertEquals("4", elements.get(6).text());
		assertEquals(EspNameTagPolicy.ABSORPTION_COLOR, elements.get(6).color());
	}

	@Test
	void healthIconFollowsItsText()
	{
		List<EspNameTagElement> elements = EspNameTagPolicy.build(ALL,
			new State(false, false, false, -1, null, 7.5F, 0));

		// 没有距离也没有名字，只剩血量
		assertEquals(1, elements.size());
		// 参考侧血量的红心也是默认的 RIGHT，画出来是「7.5♥」
		assertEquals(EspNameTagElement.IconPosition.RIGHT,
			elements.get(0).icon().position());
		assertEquals("7.5", elements.get(0).text());
	}

	@Test
	void disabledOptionsRemoveTheirElements()
	{
		Options none =
			new Options(false, false, false, false, false, false);

		assertTrue(EspNameTagPolicy
			.build(none, new State(true, true, true, 5, "Steve", 20, 4))
			.isEmpty());
	}

	@Test
	void absorptionIsSkippedWhenThereIsNone()
	{
		List<EspNameTagElement> elements = EspNameTagPolicy.build(ALL,
			new State(false, false, false, -1, null, 20, 0));

		assertEquals(1, elements.size());
		assertEquals(EspIndicatorGlyphs.HEALTH,
			elements.get(0).icon().glyph());
	}

	@Test
	void negativeDistanceIsTreatedAsUnknown()
	{
		List<EspNameTagElement> elements = EspNameTagPolicy.build(ALL,
			new State(false, false, false, -1, "Steve", 20, 0));

		// 距离未知时整条距离元素都不出现，名字直接排到最前
		assertEquals(2, elements.size());
		assertEquals("Steve", elements.get(0).text());
		assertEquals(EspIndicatorGlyphs.HEALTH,
			elements.get(1).icon().glyph());
	}

	@Test
	void blankNameIsSkipped()
	{
		List<EspNameTagElement> elements = EspNameTagPolicy.build(ALL,
			new State(false, false, false, -1, "", 20, 0));

		assertEquals(1, elements.size());
		assertEquals(EspIndicatorGlyphs.HEALTH,
			elements.get(0).icon().glyph());
	}

	@Test
	void formatHealthDropsTheTrailingZero()
	{
		assertEquals("20", EspNameTagPolicy.formatHealth(20F));
		assertEquals("0", EspNameTagPolicy.formatHealth(0F));
		assertEquals("12.3", EspNameTagPolicy.formatHealth(12.34F));
		assertEquals("12.4", EspNameTagPolicy.formatHealth(12.35F));
		assertEquals("0", EspNameTagPolicy.formatHealth(Float.NaN));
		assertEquals("0", EspNameTagPolicy.formatHealth(Float.POSITIVE_INFINITY));
	}

	/**
	 * 参考用的 {@code DecimalFormat("0.#")} 跟随默认区域设置，在以逗号作小数点的
	 * 区域会输出 {@code 12,3}。铭牌是贴在世界里的固定视觉，不该随系统语言变，
	 * 所以这里显式按 {@link Locale#ROOT} 格式化。
	 */
	@Test
	void formatHealthDoesNotFollowTheDefaultLocale()
	{
		Locale original = Locale.getDefault();
		try
		{
			Locale.setDefault(Locale.GERMANY);
			assertEquals("12.3", EspNameTagPolicy.formatHealth(12.3F));
		}finally
		{
			Locale.setDefault(original);
		}
	}

	@Test
	void healthIsRenderedWithTheDefaultColor()
	{
		List<EspNameTagElement> elements = EspNameTagPolicy.build(ALL,
			new State(false, false, false, -1, null, 20, 0));

		assertEquals(EspNameTagPolicy.DEFAULT_COLOR, elements.get(0).color());
	}

	private static String glyph(EspNameTagElement element)
	{
		assertTrue(element.hasIcon());
		assertNull(element.text());
		return element.icon().glyph();
	}
}
