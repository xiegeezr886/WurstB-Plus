package net.wurstclient.music;

import java.util.List;

/**
 * 一行里的一个卡拉 OK 词：网易云 YRC / 逐字 LRC 的时间戳。
 *
 * <p>字段对齐 AMLL {@code LyricWord}：除文本与时间戳外还有注音 {@code ruby} 与
 * 不雅词标记 {@code obscene}。</p>
 */
public record LyricWord(String text, long startMs, long endMs,
	List<LyricRuby> ruby, boolean obscene)
{
	public LyricWord
	{
		text = text == null ? "" : text;
		if(endMs < startMs)
			endMs = startMs;
		ruby = ruby == null || ruby.isEmpty() ? List.of() : List.copyOf(ruby);
	}

	/** 兼容构造：无注音、非不雅词。 */
	public LyricWord(String text, long startMs, long endMs)
	{
		this(text, startMs, endMs, List.of(), false);
	}

	public long durationMs()
	{
		return Math.max(0, endMs - startMs);
	}

	public float progressAt(long timeMs)
	{
		if(endMs <= startMs)
			return timeMs >= startMs ? 1 : 0;
		if(timeMs <= startMs)
			return 0;
		if(timeMs >= endMs)
			return 1;
		return (timeMs - startMs) / (float)(endMs - startMs);
	}

	public boolean hasRuby()
	{
		return !ruby.isEmpty();
	}

	/** 注音字符总数，AMLL 用它替换强调动画的逐字锚点数量。 */
	public int rubyCharCount()
	{
		int count = 0;
		for(LyricRuby segment : ruby)
			count += segment.text().length();
		return count;
	}

	public LyricWord withText(String newText)
	{
		return new LyricWord(newText, startMs, endMs, ruby, obscene);
	}

	public LyricWord withObscene(boolean value)
	{
		return new LyricWord(text, startMs, endMs, ruby, value);
	}

	public LyricWord withRuby(List<LyricRuby> segments)
	{
		return new LyricWord(text, startMs, endMs, segments, obscene);
	}
}
