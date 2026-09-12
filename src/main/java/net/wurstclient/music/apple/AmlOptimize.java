package net.wurstclient.music.apple;

import java.util.ArrayList;
import java.util.List;

import net.wurstclient.music.LyricLine;
import net.wurstclient.music.LyricWord;

/**
 * applemusic-like-lyrics {@code utils/optimize-lyric.ts} 的移植。
 *
 * <p>依次执行：空格规范化 → 行时间戳回填字级 → 连续背景人声降级为单行 → 主歌词与
 * 背景人声时间同步 → 按起始时间稳定排序 → 清洗非刻意重叠 → 尝试提前开始时间。</p>
 */
public final class AmlOptimize
{
	/** {@code tryAdvanceStartTime} 的默认提前量。 */
	static final long DEFAULT_ADVANCE_MS = 600;
	/** 与上一行重叠时的提前量。 */
	static final long FALLBACK_ADVANCE_MS = 400;
	/** 重叠不足 {@link #FALLBACK_ADVANCE_MS} 时按重叠时长提前的比例。 */
	static final double FALLBACK_ADVANCE_RATIO = 0.7;
	/** 视为有意重叠的最小重叠时长。 */
	static final long INTENTIONAL_OVERLAP_MS = 500;
	/** 无意重叠的绝对上限。 */
	static final long UNINTENTIONAL_OVERLAP_MS = 100;
	/** 无意重叠相对下一行时长的比例上限。 */
	static final double UNINTENTIONAL_OVERLAP_RATIO = 0.1;

	private AmlOptimize()
	{}

	/**
	 * 原地优化歌词行，返回处理后的新列表（{@link LyricLine} 不可变）。
	 */
	public static List<LyricLine> optimize(List<LyricLine> lines)
	{
		if(lines == null || lines.isEmpty())
			return List.of();
		List<Mutable> work = new ArrayList<>(lines.size());
		for(LyricLine line : lines)
			work.add(new Mutable(line));

		normalizeSpaces(work);
		resetLineTimestamps(work);
		convertExcessiveBackgroundLines(work);
		syncMainAndBackgroundLines(work);
		sortLyricLines(work);
		pairEndTimestamps(work);
		cleanUnintentionalOverlaps(work);
		tryAdvanceStartTime(work);

		List<LyricLine> result = new ArrayList<>(work.size());
		for(Mutable line : work)
			result.add(line.toLine());
		return List.copyOf(result);
	}

	/**
	 * 是否逐行歌词（非逐字）：所有行都只有一个词。
	 */
	public static boolean isNonDynamic(List<LyricLine> lines)
	{
		if(lines == null || lines.isEmpty())
			return false;
		for(LyricLine line : lines)
			if(line.words().size() > 1)
				return false;
		return true;
	}

	private static void normalizeSpaces(List<Mutable> lines)
	{
		for(Mutable line : lines)
			for(Word word : line.words)
				word.text = word.text.replaceAll("\\s+", " ");
	}

	private static void resetLineTimestamps(List<Mutable> lines)
	{
		for(Mutable line : lines)
		{
			if(line.words.isEmpty())
				continue;
			if(line.words.size() == 1)
			{
				Word only = line.words.get(0);
				if(only.start == 0 && only.end == 0
					&& (line.start != 0 || line.end != 0))
				{
					only.start = line.start;
					only.end = line.end;
					continue;
				}
			}
			line.start = line.words.get(0).start;
			line.end = line.words.get(line.words.size() - 1).end;
		}
	}

	private static void convertExcessiveBackgroundLines(List<Mutable> lines)
	{
		int consecutive = 0;
		for(int i = 0; i < lines.size(); i++)
		{
			Mutable line = lines.get(i);
			if(line.background)
			{
				consecutive++;
				if(i == 0 || consecutive > 1)
					line.background = false;
			}else
				consecutive = 0;
		}
	}

	private static void syncMainAndBackgroundLines(List<Mutable> lines)
	{
		for(int i = lines.size() - 1; i >= 0; i--)
		{
			Mutable line = lines.get(i);
			if(line.background)
				continue;
			if(i + 1 >= lines.size() || !lines.get(i + 1).background)
				continue;
			Mutable bg = lines.get(i + 1);

			long finalStart = Math.min(line.start, bg.start);
			long finalEnd = Math.max(line.end, bg.end);
			for(Word word : line.words)
			{
				if(word.text.trim().isEmpty())
					continue;
				finalStart = Math.min(finalStart, word.start);
				finalEnd = Math.max(finalEnd, word.end);
			}
			for(Word word : bg.words)
			{
				if(word.text.trim().isEmpty())
					continue;
				finalStart = Math.min(finalStart, word.start);
				finalEnd = Math.max(finalEnd, word.end);
			}

			line.start = finalStart;
			line.end = finalEnd;
			bg.start = finalStart;
			bg.end = finalEnd;
		}
	}

	private static void sortLyricLines(List<Mutable> lines)
	{
		List<List<Mutable>> groups = new ArrayList<>();
		for(int i = 0; i < lines.size(); i++)
		{
			Mutable main = lines.get(i);
			List<Mutable> group = new ArrayList<>(2);
			group.add(main);
			if(!main.background && i + 1 < lines.size()
				&& lines.get(i + 1).background)
				group.add(lines.get(++i));
			groups.add(group);
		}
		// 稳定排序：起始时间相同则保持原有组顺序
		groups.sort((a, b) -> Long.compare(a.get(0).start, b.get(0).start));
		List<Mutable> sorted = new ArrayList<>(lines.size());
		for(List<Mutable> group : groups)
			sorted.addAll(group);
		lines.clear();
		lines.addAll(sorted);
	}

	/**
	 * 与 AMLL {@code parseLrc} 一致：把没有逐字时间戳的行的结束时间对齐到下一行
	 * 的开始时间。
	 *
	 * <p>这类行在解析后 {@code end == start}，若不补齐，
	 * {@link #cleanUnintentionalOverlaps} 会因为重叠恒为负而完全失效。</p>
	 */
	private static void pairEndTimestamps(List<Mutable> lines)
	{
		for(int i = 0; i < lines.size(); i++)
		{
			Mutable line = lines.get(i);
			if(!line.words.isEmpty() || line.end > line.start)
				continue;
			long end = i + 1 < lines.size() ? lines.get(i + 1).start
				: line.start + 8_000;
			line.end = Math.max(end, line.start + 1);
		}
	}

	private static void cleanUnintentionalOverlaps(List<Mutable> lines)
	{
		for(int i = 0; i < lines.size() - 1; i++)
		{
			Mutable line = lines.get(i);
			if(line.background)
				continue;
			for(int j = i + 1; j < lines.size(); j++)
			{
				Mutable next = lines.get(j);
				if(next.background)
					continue;
				long overlap = line.end - next.start;
				if(overlap <= 0)
					break;
				long nextDuration = next.end - next.start;
				double percentageThreshold = nextDuration
					* UNINTENTIONAL_OVERLAP_RATIO;
				boolean intentional = overlap >= INTENTIONAL_OVERLAP_MS
					|| overlap > UNINTENTIONAL_OVERLAP_MS
						&& overlap > percentageThreshold;
				if(!intentional)
				{
					line.end = next.start;
					if(i + 1 < lines.size() && lines.get(i + 1).background)
						lines.get(i + 1).end = next.start;
					break;
				}
			}
		}
	}

	private static void tryAdvanceStartTime(List<Mutable> lines)
	{
		boolean hasPrev = false;
		long prevLineStart = 0;
		long prevLineEnd = 0;
		long prevGroupStart = 0;
		long prevGroupEnd = 0;

		for(Mutable line : lines)
		{
			if(line.background)
				continue;

			long originalStart = line.start;
			long originalEnd = line.end;

			long advance;
			long safeBoundary;
			if(hasPrev)
			{
				boolean hadGap = originalStart >= prevLineEnd;
				if(hadGap)
				{
					advance = DEFAULT_ADVANCE_MS;
					safeBoundary = prevGroupEnd;
				}else
				{
					long overlap = prevLineEnd - originalStart;
					advance = overlap < FALLBACK_ADVANCE_MS
						? Math.round(overlap * FALLBACK_ADVANCE_RATIO)
						: FALLBACK_ADVANCE_MS;
					safeBoundary = prevLineStart;
				}
			}else
			{
				advance = DEFAULT_ADVANCE_MS;
				safeBoundary = 0;
			}

			long target = line.start - advance;
			long newStart = Math.max(safeBoundary, target);
			if(newStart < line.start)
				line.start = newStart;

			int index = lines.indexOf(line);
			if(index >= 0 && index + 1 < lines.size()
				&& lines.get(index + 1).background)
				lines.get(index + 1).start = line.start;

			if(hasPrev)
			{
				boolean overlapsGroup = originalStart < prevGroupEnd
					&& originalEnd > prevGroupStart;
				if(overlapsGroup)
				{
					prevGroupStart = Math.min(prevGroupStart, originalStart);
					prevGroupEnd = Math.max(prevGroupEnd, originalEnd);
				}else
				{
					prevGroupStart = originalStart;
					prevGroupEnd = originalEnd;
				}
			}else
			{
				prevGroupStart = originalStart;
				prevGroupEnd = originalEnd;
			}

			prevLineStart = line.start;
			prevLineEnd = originalEnd;
			hasPrev = true;
		}
	}

	/** 可变词，用于原地优化。 */
	private static final class Word
	{
		String text;
		long start;
		long end;
		final List<net.wurstclient.music.LyricRuby> ruby;
		final boolean obscene;

		Word(LyricWord word)
		{
			text = word.text();
			start = word.startMs();
			end = word.endMs();
			ruby = word.ruby();
			obscene = word.obscene();
		}

		LyricWord toWord()
		{
			return new LyricWord(text, start, end, ruby, obscene);
		}
	}

	/** 可变歌词行。 */
	private static final class Mutable
	{
		long start;
		long end;
		String text;
		String translation;
		String romanization;
		boolean background;
		final List<Word> words = new ArrayList<>();

		Mutable(LyricLine line)
		{
			start = line.timeMs();
			end = line.endTimeMs();
			text = line.text();
			translation = line.translatedLyric();
			romanization = line.romanLyric();
			background = line.background();
			for(LyricWord word : line.words())
				words.add(new Word(word));
		}

		LyricLine toLine()
		{
			List<LyricWord> converted = new ArrayList<>(words.size());
			for(Word word : words)
				converted.add(word.toWord());
			StringBuilder joined = new StringBuilder();
			for(Word word : words)
				joined.append(word.text);
			String finalText = joined.length() > 0 ? joined.toString() : text;
			return new LyricLine(start, finalText, converted, end, translation,
				romanization, background);
		}
	}
}
