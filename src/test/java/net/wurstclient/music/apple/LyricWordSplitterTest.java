package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

final class LyricWordSplitterTest
{
	@Test
	void splitsCjkCharactersAndKeepsLatinRuns()
	{
		assertEquals(List.of("海", "阔", "天", "空", "Beyond"),
			LyricWordSplitter.split("海阔天空 Beyond"));
		assertEquals(List.of("Hello", "世", "界"),
			LyricWordSplitter.split("Hello\u3000世界"));
		assertEquals(List.of(), LyricWordSplitter.split("   "));
	}

	@Test
	void wordFractionsScaleByCharacterLength()
	{
		float[][] fractions = LyricWordSplitter.wordFractions(
			List.of("AB", "海"));
		assertEquals(2, fractions.length);
		assertArrayEquals(new float[]{0F, 2F / 3F}, fractions[0], 1e-6F);
		assertArrayEquals(new float[]{2F / 3F, 1F}, fractions[1], 1e-6F);
		assertEquals(0, LyricWordSplitter.wordFractions(List.of()).length);
		assertEquals(0, LyricWordSplitter.wordFractions(null).length);
	}

	@Test
	void treatsNullAndPunctuationAsNonCjkRuns()
	{
		assertEquals(List.of(), LyricWordSplitter.split(null));
		assertEquals(List.of(), LyricWordSplitter.split(""));
		assertEquals(List.of("OK!", "好"), LyricWordSplitter.split("OK!好"));
	}

	@Test
	void treatsUnifiedIdeographsAsCjk()
	{
		assertTrue(LyricWordSplitter.isCJK('海'));
		assertTrue(LyricWordSplitter.isCJK('\u4E00'));
		assertFalse(LyricWordSplitter.isCJK('A'));
		assertFalse(LyricWordSplitter.isCJK(' '));
	}
}
