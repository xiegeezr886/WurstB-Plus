package net.wurstclient.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

final class LyricParserTest
{
	@Test
	void parsesMultipleTimestampFormatsAndSortsLines()
	{
		List<LyricLine> lines = LyricParser.parse(
			"[01:02.50]Second\n[00:03.125][00:04]First");

		assertEquals(List.of(new LyricLine(3125, "First"),
			new LyricLine(4000, "First"), new LyricLine(62500, "Second")),
			lines);
	}

	@Test
	void findsCurrentLineWithBinarySearch()
	{
		List<LyricLine> lines = List.of(new LyricLine(1000, "A"),
			new LyricLine(2000, "B"), new LyricLine(3000, "C"));
		assertEquals(-1, LyricParser.findCurrentIndex(lines, 999));
		assertEquals(0, LyricParser.findCurrentIndex(lines, 1000));
		assertEquals(1, LyricParser.findCurrentIndex(lines, 2500));
		assertEquals(2, LyricParser.findCurrentIndex(lines, 9999));
		assertEquals(-1, LyricParser.findCurrentIndex(List.of(), 0));
		assertEquals(-1, LyricParser.findCurrentIndex(null, 0));
	}

	@Test
	void skipsBlankSourceAndTimestampOnlyLines()
	{
		assertEquals(List.of(), LyricParser.parse(null));
		assertEquals(List.of(), LyricParser.parse("   "));
		assertEquals(List.of(), LyricParser.parse("[00:01.00]\n[00:02]"));
	}

	@Test
	void scalesFractionalSecondsByDigitCount()
	{
		List<LyricLine> lines = LyricParser.parse(
			"[00:01.5]One\n[00:01.50]Two\n[00:01.500]Three\n[00:01]Four");
		assertEquals(List.of(new LyricLine(1000, "Four"),
			new LyricLine(1500, "One"), new LyricLine(1500, "Two"),
			new LyricLine(1500, "Three")), lines);
	}

	@Test
	void parsesNeteaseYrcWordTimings()
	{
		List<LyricLine> lines = LyricParser.parseYrc(
			"[1670,590](1670,240,0)人(1910,350,0)生");
		assertEquals(1, lines.size());
		assertEquals(1670, lines.get(0).timeMs());
		assertEquals("人生", lines.get(0).text());
		assertEquals(List.of(new LyricWord("人", 1670, 1910),
			new LyricWord("生", 1910, 2260)), lines.get(0).words());
		assertEquals(2260, lines.get(0).endTimeMs());
	}

	@Test
	void parsesYrcBackgroundLinesAndAttachesTranslations()
	{
		List<LyricLine> lines = LyricParser.parseYrc(
			"[1000,400](1000,400,0)（背景）");
		assertEquals(1, lines.size());
		assertTrue(lines.get(0).background());
		assertEquals("背景", lines.get(0).text());

		List<LyricLine> merged = LyricParser.parseBest(
			"[1000,400](1000,400,0)Hello", "[00:01.00]Hello",
			"[00:01.00]你好");
		assertEquals("你好", merged.get(0).translatedLyric());
	}

	@Test
	void parsesEnhancedLrcWordTags()
	{
		List<LyricLine> lines = LyricParser.parse(
			"[00:01.00]<1000,200>Hello<1200,180> World");
		assertEquals(1, lines.size());
		assertEquals("Hello World", lines.get(0).text());
		assertEquals(List.of(new LyricWord("Hello", 1000, 1200),
			new LyricWord(" World", 1200, 1380)), lines.get(0).words());
	}

	@Test
	void prefersYrcOverPlainLrc()
	{
		List<LyricLine> lines = LyricParser.parseBest(
			"[0,400](0,400,0)Yrc", "[00:00.00]Plain");
		assertEquals("Yrc", lines.get(0).text());
		assertTrue(lines.get(0).hasWordTimings());
		assertEquals(List.of(new LyricLine(0, "Plain")),
			LyricParser.parseBest("", "[00:00.00]Plain"));
	}

	@Test
	void attachesRomanizationFromRomaLrc()
	{
		List<LyricLine> lines = LyricParser.parseBest(
			"[1000,400](1000,400,0)人生", "", "[00:01.00]人生",
			"[00:01.00]ren sheng");
		assertEquals(1, lines.size());
		assertEquals("人生", lines.get(0).translatedLyric());
		assertEquals("ren sheng", lines.get(0).romanLyric());
		assertTrue(lines.get(0).hasRomanization());
	}

	@Test
	void missingRomanizationLeavesLinesUntouched()
	{
		List<LyricLine> lines = LyricParser.parseBest("", "[00:01.00]Hello",
			null, null);
		assertEquals(1, lines.size());
		assertFalse(lines.get(0).hasRomanization());
	}

	@Test
	void wordProgressClampsToUnitInterval()
	{
		LyricWord word = new LyricWord("海", 1000, 2000);
		assertEquals(0, word.progressAt(500), 1e-6);
		assertEquals(0.5F, word.progressAt(1500), 1e-6);
		assertEquals(1, word.progressAt(2500), 1e-6);
	}
}
