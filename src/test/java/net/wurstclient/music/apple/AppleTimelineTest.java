package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AppleTimelineTest
{
	@Test
	void playbackHighlightsTheActiveLineAndScrollsToIt()
	{
		AppleTimeline timeline = timeline(
			new long[]{0, 1_000, 2_000},
			new long[]{900, 1_800, 2_800});

		timeline.sync(500, false);
		assertTrue(timeline.isPlaying(0));
		assertTrue(timeline.isHighlighted(0));
		assertEquals(0, timeline.scrollToIndex());
		assertFalse(timeline.isEndOfSong());

		timeline.sync(1_200, false);
		assertFalse(timeline.isPlaying(0));
		assertFalse(timeline.isHighlighted(0));
		assertTrue(timeline.isPlaying(1));
		assertTrue(timeline.isHighlighted(1));
		assertEquals(1, timeline.scrollToIndex());
	}

	@Test
	void seekJumpsIntoAGapAndTreatsLongSilenceAsInterlude()
	{
		AppleTimeline timeline = timeline(
			new long[]{0, 8_000},
			new long[]{1_000, 9_000});

		timeline.sync(4_000, true);
		assertTrue(timeline.isSeeking());
		assertTrue(timeline.isTimeJumped());
		assertTrue(timeline.isTimelineEmpty());
		assertEquals(1, timeline.scrollToIndex());
		assertEquals(0, timeline.activeInterlude());
		assertTrue(timeline.isFocusOnInterlude());
		assertEquals(0, timeline.interludeAnchorLine());
		assertEquals(1_000, timeline.interludeStart());
		assertEquals(8_000, timeline.interludeEnd());
	}

	@Test
	void crossingTheLastEndMarksTheSongFinished()
	{
		AppleTimeline timeline = timeline(
			new long[]{0, 1_000},
			new long[]{900, 1_500});
		timeline.sync(1_200, false);
		assertTrue(timeline.isPlaying(1));
		timeline.sync(1_500, false);
		assertTrue(timeline.isEndOfSong());
		assertTrue(timeline.isTimelineEmpty());
	}

	private static AppleTimeline timeline(long[] starts, long[] ends)
	{
		AppleTimeline timeline = new AppleTimeline();
		timeline.setTimeBounds(starts, ends);
		return timeline;
	}
}
