package net.wurstclient.music.apple;

import java.util.ArrayList;
import java.util.List;

/**
 * applemusic-like-lyrics {@code is-cjk.ts} + {@code lyric-split-words.ts}
 * 分词核心的 1:1 Java 移植。
 *
 * <p>CJK 统一表意文字逐字成词；连续非 CJK 字符（英文/数字）合并为一个词；
 * 空白作为词间分隔。</p>
 */
public final class LyricWordSplitter
{
	private LyricWordSplitter()
	{}

	/** 对应 is-cjk.ts：Unified_Ideograph + 0x0800-0x9FFC 区间。 */
	public static boolean isCJK(char c)
	{
		if(c >= 0x0800 && c <= 0x9FFC)
			return true;
		// Unicode 统一表意文字基本区与扩展
		return c >= 0x3400 && c <= 0x4DBF || c >= 0x4E00 && c <= 0x9FFF
			|| c >= 0xF900 && c <= 0xFAFF || c >= 0x20000 && c <= 0x2FA1F;
	}

	/**
	 * 将歌词行拆分为词序列。CJK 单字成词，连续非 CJK 合并，空白跳过
	 * （作为词间分隔）。
	 */
	public static List<String> split(String line)
	{
		List<String> words = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for(int i = 0; i < line.length(); i++)
		{
			char c = line.charAt(i);
			if(isCJK(c))
			{
				if(current.length() > 0)
				{
					words.add(current.toString());
					current.setLength(0);
				}
				words.add(String.valueOf(c));
			}else if(c == ' ' || c == '　')
			{
				if(current.length() > 0)
				{
					words.add(current.toString());
					current.setLength(0);
				}
			}else
				current.append(c);
		}
		if(current.length() > 0)
			words.add(current.toString());
		return words;
	}

	/**
	 * 按词长度比例把行时长均分，返回每个词的 [startFraction, endFraction]。
	 * 用于行级时间戳下模拟词级时间（无词级 LRC 时的近似，与优化器语义一致）。
	 */
	public static float[][] wordFractions(List<String> words)
	{
		int total = 0;
		for(String word : words)
			total += word.length();
		if(total == 0)
			return new float[0][];
		float[][] fractions = new float[words.size()][2];
		float cursor = 0;
		for(int i = 0; i < words.size(); i++)
		{
			float length = words.get(i).length() / (float)total;
			fractions[i][0] = cursor;
			cursor += length;
			fractions[i][1] = cursor;
		}
		return fractions;
	}
}
