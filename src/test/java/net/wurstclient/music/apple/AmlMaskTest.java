package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.wurstclient.music.LyricLine;
import net.wurstclient.music.LyricWord;
import org.junit.jupiter.api.Test;

/**
 * 校验 {@link AmlMask} 复刻的 AMLL 不雅词掩码。
 */
final class AmlMaskTest
{
	private static LyricWord word(String text, boolean obscene)
	{
		return new LyricWord(text, 0, 100).withObscene(obscene);
	}

	private static String masked(String text, AmlMask.Mode mode)
	{
		LyricWord result = AmlMask.maskWord(word(text, true), mode, '*');
		return result.text();
	}

	@Test
	void disabledModeLeavesTextAlone()
	{
		assertEquals("fuck", masked("fuck", AmlMask.Mode.DISABLED));
	}

	@Test
	void fullMaskReplacesEveryNonSpaceCharacter()
	{
		assertEquals("****", masked("fuck", AmlMask.Mode.FULL));
		// 空白保留，否则会改变断行
		assertEquals("** **", masked("ab cd", AmlMask.Mode.FULL));
	}

	@Test
	void partialMaskKeepsFirstAndLastCharacter()
	{
		assertEquals("f**k", masked("fuck", AmlMask.Mode.PARTIAL));
		assertEquals("s***t", masked("shoot", AmlMask.Mode.PARTIAL));
	}

	@Test
	void partialMaskFallsBackToFullForShortWords()
	{
		// 长度 ≤ 2 时保留首尾等于没遮，因此整词掩码
		assertEquals("**", masked("ab", AmlMask.Mode.PARTIAL));
		assertEquals("*", masked("a", AmlMask.Mode.PARTIAL));
	}

	@Test
	void partialMaskKeepsSurroundingWhitespace()
	{
		assertEquals(" f**k ", masked(" fuck ", AmlMask.Mode.PARTIAL));
	}

	@Test
	void cleanWordsAreUntouched()
	{
		assertEquals("hello", AmlMask.maskWord(word("hello", false),
			AmlMask.Mode.FULL, '*').text());
	}

	@Test
	void applyReturnsNewLinesOnlyWhenNeeded()
	{
		List<LyricLine> clean = List.of(new LyricLine(0, "hello",
			List.of(new LyricWord("hello", 0, 100)), 100, "", "", false));
		// 没有不雅词时不复制，直接返回原列表
		assertTrue(AmlMask.apply(clean, AmlMask.Mode.FULL) == clean);

		List<LyricLine> dirty = List.of(new LyricLine(0, "fuck",
			List.of(word("fuck", true)), 100, "", "", false));
		List<LyricLine> result = AmlMask.apply(dirty, AmlMask.Mode.PARTIAL);
		assertEquals("f**k", result.get(0).words().get(0).text());
		assertEquals("f**k", result.get(0).text());
	}

	@Test
	void applyPreservesEmptyInput()
	{
		assertTrue(AmlMask.apply(List.of(), AmlMask.Mode.FULL).isEmpty());
		assertTrue(AmlMask.apply(null, AmlMask.Mode.FULL).isEmpty());
	}

	@Test
	void rubyAndObsceneFlagsSurviveMasking()
	{
		LyricWord withRuby = new LyricWord("fuck", 0, 100,
			List.of(new net.wurstclient.music.LyricRuby("fa", 0, 50)), true);
		LyricWord result = AmlMask.maskWord(withRuby, AmlMask.Mode.PARTIAL, '#');
		assertEquals("f##k", result.text());
		assertTrue(result.obscene());
		assertEquals(1, result.ruby().size());
	}

	@Test
	void defaultMaskCharIsAsterisk()
	{
		LyricWord result = AmlMask
			.apply(List.of(new LyricLine(0, "fuck", List.of(word("fuck", true)),
				100, "", "", false)), AmlMask.Mode.FULL)
			.get(0).words().get(0);
		assertEquals("****", result.text());
		assertFalse(result.text().contains("f"));
	}
}
