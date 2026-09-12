package net.wurstclient.music;

import java.util.List;

/**
 * 一行歌词。字段对齐 applemusic-like-lyrics {@code LyricLine}：
 * 词级时间戳、行结束时间、翻译、音译、背景人声。
 */
public record LyricLine(long timeMs, String text, List<LyricWord> words,
	long endTimeMs, String translatedLyric, String romanLyric,
	boolean background)
{
	public LyricLine
	{
		text = text == null ? "" : text;
		words = words == null || words.isEmpty() ? List.of()
			: List.copyOf(words);
		translatedLyric = translatedLyric == null ? "" : translatedLyric;
		romanLyric = romanLyric == null ? "" : romanLyric;
		if(endTimeMs < timeMs)
			endTimeMs = timeMs;
	}

	/** 兼容构造：无音译。 */
	public LyricLine(long timeMs, String text, List<LyricWord> words,
		long endTimeMs, String translatedLyric, boolean background)
	{
		this(timeMs, text, words, endTimeMs, translatedLyric, "", background);
	}

	public LyricLine(long timeMs, String text)
	{
		this(timeMs, text, List.of(), timeMs, "", "", false);
	}

	public LyricLine(long timeMs, String text, List<LyricWord> words)
	{
		this(timeMs, text, words, lastWordEnd(timeMs, words), "", "", false);
	}

	public LyricLine withTranslation(String translation)
	{
		return new LyricLine(timeMs, text, words, endTimeMs, translation,
			romanLyric, background);
	}

	public LyricLine withRomanization(String roman)
	{
		return new LyricLine(timeMs, text, words, endTimeMs, translatedLyric,
			roman, background);
	}

	public LyricLine withEndTime(long end)
	{
		return new LyricLine(timeMs, text, words, end, translatedLyric,
			romanLyric, background);
	}

	public LyricLine withWords(List<LyricWord> newWords)
	{
		return new LyricLine(timeMs, text, newWords, endTimeMs, translatedLyric,
			romanLyric, background);
	}

	public boolean hasWordTimings()
	{
		return !words.isEmpty();
	}

	public boolean hasTranslation()
	{
		return !translatedLyric.isBlank();
	}

	public boolean hasRomanization()
	{
		return !romanLyric.isBlank();
	}

	public boolean hasRuby()
	{
		for(LyricWord word : words)
			if(word.hasRuby())
				return true;
		return false;
	}

	public long endMs(long fallback)
	{
		if(endTimeMs > timeMs)
			return endTimeMs;
		if(!words.isEmpty())
			return Math.max(timeMs, words.get(words.size() - 1).endMs());
		return fallback;
	}

	private static long lastWordEnd(long timeMs, List<LyricWord> words)
	{
		if(words == null || words.isEmpty())
			return timeMs;
		return Math.max(timeMs, words.get(words.size() - 1).endMs());
	}
}
