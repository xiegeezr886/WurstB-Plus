package net.wurstclient.music.apple;

import java.util.ArrayList;
import java.util.List;

import net.wurstclient.music.LyricLine;
import net.wurstclient.music.LyricWord;

/**
 * applemusic-like-lyrics {@code lyric-player/base/lyric-data-manager.ts} 中
 * {@code applyMask} 与 {@code MaskObsceneWordsMode} 的移植。
 *
 * <p>掩码在渲染**之前**应用到文本上：掩码会改变字形宽度，若在渲染时才替换，
 * 视觉宽度与实际渲染宽度就会错位；同时被掩码的音节也不应再套用掩码前字形的
 * 强调效果。</p>
 */
public final class AmlMask
{
	/** 不雅用语掩码模式，取值与 AMLL 一致。 */
	public enum Mode
	{
		/** 禁用任何掩码。 */
		DISABLED,
		/** 完全掩码所有不雅用语。 */
		FULL,
		/** 保留首尾字符，屏蔽中间字符。 */
		PARTIAL
	}

	public static final char DEFAULT_MASK_CHAR = '*';

	private AmlMask()
	{}

	public static List<LyricLine> apply(List<LyricLine> lines, Mode mode)
	{
		return apply(lines, mode, DEFAULT_MASK_CHAR);
	}

	/**
	 * 返回应用掩码后的新歌词列表；{@code DISABLED} 时原样返回。
	 */
	public static List<LyricLine> apply(List<LyricLine> lines, Mode mode,
		char maskChar)
	{
		if(lines == null || lines.isEmpty() || mode == null
			|| mode == Mode.DISABLED || !hasObscene(lines))
			return lines == null ? List.of() : lines;

		List<LyricLine> masked = new ArrayList<>(lines.size());
		for(LyricLine line : lines)
		{
			List<LyricWord> words = new ArrayList<>(line.words().size());
			StringBuilder joined = new StringBuilder();
			for(LyricWord word : line.words())
			{
				LyricWord maskedWord = maskWord(word, mode, maskChar);
				words.add(maskedWord);
				joined.append(maskedWord.text());
			}
			// 行文本与词文本保持一致，否则非逐字路径会渲染出未掩码的原文
			String text = words.isEmpty() ? line.text() : joined.toString();
			masked.add(new LyricLine(line.timeMs(), text, words,
				line.endTimeMs(), line.translatedLyric(), line.romanLyric(),
				line.background()));
		}
		return List.copyOf(masked);
	}

	private static boolean hasObscene(List<LyricLine> lines)
	{
		for(LyricLine line : lines)
			for(LyricWord word : line.words())
				if(word.obscene())
					return true;
		return false;
	}

	/**
	 * 单个词的掩码，逐条对应 AMLL {@code LyricDataManager.applyMask}。
	 *
	 * <p>注音（ruby）与逐字音译刻意不参与掩码：注音通常不会直接包含原不雅词，
	 * 且逐行音译本就无法定位到词内。</p>
	 */
	static LyricWord maskWord(LyricWord word, Mode mode, char maskChar)
	{
		if(!word.obscene() || mode == Mode.DISABLED)
			return word;

		String text = word.text();
		if(mode == Mode.FULL)
			return word.withText(replaceNonSpace(text, maskChar));

		String trimmed = text.trim();
		if(trimmed.length() <= 2)
			return word.withText(replaceNonSpace(text, maskChar));

		int startPos = text.indexOf(trimmed);
		if(startPos < 0)
			return word.withText(replaceNonSpace(text, maskChar));
		int endPos = startPos + trimmed.length() - 1;
		return word.withText(text.substring(0, startPos + 1)
			+ replaceNonSpace(text.substring(startPos + 1, endPos), maskChar)
			+ text.substring(endPos));
	}

	/** 等价于 JS {@code text.replace(/\S/g, maskChar)}。 */
	private static String replaceNonSpace(String text, char maskChar)
	{
		StringBuilder builder = new StringBuilder(text.length());
		for(int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			builder.append(Character.isWhitespace(c) ? c : maskChar);
		}
		return builder.toString();
	}
}
