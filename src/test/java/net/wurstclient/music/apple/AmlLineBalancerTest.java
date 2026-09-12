package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 校验 {@link AmlLineBalancer} 复刻的 AMLL 断行平衡。
 */
final class AmlLineBalancerTest
{
	private static AmlLineBalancer.Token[] evenTokens(int count, double width)
	{
		AmlLineBalancer.Token[] tokens = new AmlLineBalancer.Token[count];
		for(int i = 0; i < count; i++)
			tokens[i] = new AmlLineBalancer.Token("字", width, false, true);
		return tokens;
	}

	/** 按断点切分并返回每一行的宽度，用于验证不超宽。 */
	private static List<Double> rowWidths(AmlLineBalancer.Token[] tokens,
		int[] breaks)
	{
		List<Double> widths = new ArrayList<>();
		double current = 0;
		int breakCursor = 0;
		for(int i = 0; i < tokens.length; i++)
		{
			if(i > 0 && breakCursor < breaks.length && breaks[breakCursor] == i)
			{
				widths.add(current);
				current = 0;
				breakCursor++;
			}
			current += tokens[i].width();
		}
		widths.add(current);
		return widths;
	}

	@Test
	void linesThatFitProduceNoBreaks()
	{
		AmlLineBalancer.Token[] tokens = evenTokens(4, 10);
		assertEquals(0, AmlLineBalancer.breaks(tokens, 100).length);
	}

	@Test
	void balancesTwoRowsEvenly()
	{
		AmlLineBalancer.Token[] tokens = evenTokens(8, 10);
		int[] breaks = AmlLineBalancer.breaks(tokens, 40);
		// 8 个 10px 片段放进 40px 容器 → 正中间断开
		assertEquals(1, breaks.length);
		assertEquals(4, breaks[0]);
		List<Double> widths = rowWidths(tokens, breaks);
		assertEquals(2, widths.size());
		assertEquals(40, widths.get(0), 1e-6);
		assertEquals(40, widths.get(1), 1e-6);
	}

	@Test
	void everyRowStaysWithinContainer()
	{
		AmlLineBalancer.Token[] tokens = evenTokens(11, 13);
		int[] breaks = AmlLineBalancer.breaks(tokens, 50);
		assertTrue(breaks.length > 0);
		for(double width : rowWidths(tokens, breaks))
			assertTrue(width <= 50 + 1e-6,
				"整行宽度不应超过容器：" + width);
	}

	@Test
	void emptyOrDegenerateInputIsSafe()
	{
		assertEquals(0, AmlLineBalancer.breaks(null, 100).length);
		assertEquals(0, AmlLineBalancer.breaks(new AmlLineBalancer.Token[0], 100)
			.length);
		assertEquals(0,
			AmlLineBalancer.breaks(evenTokens(2, 10), 0).length);
	}

	@Test
	void singleOverflowingTokenDoesNotBreak()
	{
		AmlLineBalancer.Token[] tokens = {
			new AmlLineBalancer.Token("超长词", 200, false, true)};
		assertEquals(0, AmlLineBalancer.breaks(tokens, 40).length);
	}

	@Test
	void prefersBreakingAfterPunctuation()
	{
		// 前半段以逗号结尾，容器刚好放不下整句 → 应优先在逗号后的片段处断开
		AmlLineBalancer.Token[] tokens = {
			new AmlLineBalancer.Token("前", 20, false, true),
			new AmlLineBalancer.Token("半，", 20, false, true),
			new AmlLineBalancer.Token("后", 20, false, true),
			new AmlLineBalancer.Token("半", 20, false, true)};
		int[] breaks = AmlLineBalancer.breaks(tokens, 50);
		assertEquals(1, breaks.length);
		// 索引 2 正好是「后」，其前一片段以逗号结尾
		assertEquals(2, breaks[0]);
	}

	@Test
	void recognisesPunctuationTail()
	{
		assertTrue(AmlLineBalancer.isPunctuationEnd("好，"));
		assertTrue(AmlLineBalancer.isPunctuationEnd("end."));
		assertFalse(AmlLineBalancer.isPunctuationEnd("好"));
		assertFalse(AmlLineBalancer.isPunctuationEnd(""));
		assertFalse(AmlLineBalancer.isPunctuationEnd(null));
	}
}
