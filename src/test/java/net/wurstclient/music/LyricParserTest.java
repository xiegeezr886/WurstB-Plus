package net.wurstclient.music;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
		assertEquals(1, LyricParser.findCurrentIndex(lines, 2500));
		assertEquals(2, LyricParser.findCurrentIndex(lines, 9999));
	}
}
