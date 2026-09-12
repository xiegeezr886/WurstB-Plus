package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.wurstclient.music.LyricLine;
import org.junit.jupiter.api.Test;

final class AppleLyricPlayerTest
{
	@Test
	void emptyLyricsNeverHitALine()
	{
		AppleLyricPlayer player = new AppleLyricPlayer();
		assertEquals(-1, player.hitLine(20, 0, 100));
		player.setLyricLines(List.of(), 0);
		assertEquals(-1, player.hitLine(20, 0, 100));
		assertEquals(0, player.lineTime(0));
	}

	@Test
	void lineTimesFollowParsedStarts()
	{
		AppleLyricPlayer player = new AppleLyricPlayer();
		player.setLyricLines(List.of(new LyricLine(1200, "A"),
			new LyricLine(3400, "B")), 1200);
		assertEquals(1200, player.lineTime(0));
		assertEquals(3400, player.lineTime(1));
		assertEquals(0, player.lineTime(-1));
	}

	@Test
	void emphasizesLongWordsLikeAml()
	{
		assertTrue(AppleLyricPlayer.shouldEmphasize(
			new net.wurstclient.music.LyricWord("海", 0, 1200)));
		assertFalse(AppleLyricPlayer.shouldEmphasize(
			new net.wurstclient.music.LyricWord("海", 0, 400)));
		assertFalse(AppleLyricPlayer.shouldEmphasize(
			new net.wurstclient.music.LyricWord("A", 0, 2000)));
		assertTrue(AppleLyricPlayer.shouldEmphasize(
			new net.wurstclient.music.LyricWord("Hello", 0, 1600)));
	}
}
