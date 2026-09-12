package net.wurstclient.music;

/**
 * 一个词的注音片段（日文假名 / 拼音），对应 AMLL {@code LyricWord.ruby} 数组中的元素。
 *
 * <p>Apple Music 的 TTML 会给出逐音节注音与时间戳；网易云接口不提供该字段，
 * 因此解析器目前不会填充它，渲染层仍然按 AMLL 的规则预留了位置。</p>
 */
public record LyricRuby(String text, long startMs, long endMs)
{
	public LyricRuby
	{
		text = text == null ? "" : text;
		if(endMs < startMs)
			endMs = startMs;
	}

	public LyricRuby(String text)
	{
		this(text, 0, 0);
	}

	public long durationMs()
	{
		return Math.max(0, endMs - startMs);
	}

	public boolean isBlank()
	{
		return text.trim().isEmpty();
	}
}
