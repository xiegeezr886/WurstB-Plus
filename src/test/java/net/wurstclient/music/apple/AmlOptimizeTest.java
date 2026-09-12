package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.wurstclient.music.LyricLine;
import net.wurstclient.music.LyricWord;
import org.junit.jupiter.api.Test;

/**
 * 校验 {@link AmlOptimize} 复刻的 AMLL {@code optimizeLyricLines} 流水线。
 */
final class AmlOptimizeTest
{
	private static LyricLine plain(long start, long end, String text)
	{
		return new LyricLine(start, text, List.of(), end, null, false);
	}

	private static LyricLine background(long start, long end, String text)
	{
		return new LyricLine(start, text, List.of(), end, null, true);
	}

	private static LyricLine withWords(String text, long start, long end,
		boolean bg, long... wordBounds)
	{
		List<LyricWord> words = new java.util.ArrayList<>();
		for(int i = 0; i + 1 < wordBounds.length; i += 2)
			words.add(new LyricWord("字" + i, wordBounds[i], wordBounds[i + 1]));
		return new LyricLine(start, text, words, end, null, bg);
	}

	@Test
	void advancesStartTimeOfGappedLines()
	{
		List<LyricLine> optimized = AmlOptimize
			.optimize(List.of(plain(0, 1000, "A"), plain(5000, 6000, "B")));
		assertEquals(2, optimized.size());
		assertEquals(0, optimized.get(0).timeMs());
		// 与上一行存在充足间隔 → 提前 600ms
		assertEquals(4400, optimized.get(1).timeMs());
	}

	@Test
	void neverAdvancesPastThePreviousGroupEnd()
	{
		// 上一行结束于 1000，B 的开始时间被安全边界挡住，不会被拉到 1000 之前
		List<LyricLine> optimized = AmlOptimize
			.optimize(List.of(plain(0, 1000, "A"), plain(1100, 2000, "B")));
		assertTrue(optimized.get(1).timeMs() >= 1000);
	}

	@Test
	void truncatesUnintentionalOverlap()
	{
		List<LyricLine> optimized = AmlOptimize
			.optimize(List.of(plain(0, 3000, "A"), plain(2950, 3950, "B")));
		// 50ms 重叠远低于阈值 → A 被截断到 B 的开始时间
		assertEquals(2950, optimized.get(0).endTimeMs());
	}

	@Test
	void keepsIntentionalOverlap()
	{
		List<LyricLine> optimized = AmlOptimize
			.optimize(List.of(plain(0, 3000, "A"), plain(2000, 4000, "B")));
		// 1000ms 重叠 ≥ 500ms → 视为有意，不截断
		assertEquals(3000, optimized.get(0).endTimeMs());
	}

	@Test
	void demotesLeadingAndConsecutiveBackgroundLines()
	{
		List<LyricLine> optimized = AmlOptimize.optimize(List.of(
			background(0, 1000, "第一行不能是背景"), background(100, 1100, "连续背景"),
			plain(2000, 3000, "主歌词")));
		assertEquals(3, optimized.size());
		assertFalse(optimized.get(0).background());
		assertFalse(optimized.get(1).background());
		assertFalse(optimized.get(2).background());
	}

	@Test
	void syncsMainAndBackgroundTimes()
	{
		List<LyricLine> optimized = AmlOptimize.optimize(List.of(
			withWords("主歌词", 1000, 2000, false, 1000, 1500, 1500, 2000),
			withWords("背景人声", 800, 2200, true, 800, 1000, 1000, 2200)));
		assertEquals(2, optimized.size());
		// 主歌词与背景人声取并集后同起同止
		assertEquals(optimized.get(0).timeMs(), optimized.get(1).timeMs());
		assertEquals(optimized.get(0).endTimeMs(),
			optimized.get(1).endTimeMs());
		assertEquals(2200, optimized.get(0).endTimeMs());
		assertTrue(optimized.get(1).background());
	}

	@Test
	void detectsNonDynamicLyrics()
	{
		assertTrue(AmlOptimize.isNonDynamic(
			List.of(plain(0, 1000, "A"), plain(1000, 2000, "B"))));
		assertFalse(AmlOptimize
			.isNonDynamic(List.of(withWords("逐字", 0, 2000, false, 0, 1000,
				1000, 2000))));
		assertFalse(AmlOptimize.isNonDynamic(List.of()));
		assertFalse(AmlOptimize.isNonDynamic(null));
	}

	@Test
	void pairsEndTimestampsLikeParseLrc()
	{
		List<LyricLine> optimized = AmlOptimize.optimize(List.of(
			new LyricLine(0, "A", List.of(), 0, null, false),
			new LyricLine(4000, "B", List.of(), 4000, null, false)));
		// 无逐字时间戳的行把结束时间补齐为下一行的开始时间
		assertEquals(4000, optimized.get(0).endTimeMs());
		// 最后一行补齐 8 秒
		assertEquals(12000, optimized.get(1).endTimeMs());
	}

	@Test
	void emptyInputProducesEmptyOutput()
	{
		assertTrue(AmlOptimize.optimize(List.of()).isEmpty());
		assertTrue(AmlOptimize.optimize(null).isEmpty());
	}
}
