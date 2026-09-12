package net.wurstclient.music.apple;

/**
 * applemusic-like-lyrics {@code utils/lyric-line-break.ts} 的
 * {@code calcBalancedBreaks} 移植：用动态规划挑选换行点，让各行长度尽量接近，
 * 并按「标点 &gt; 空格 &gt; CJK 词界 &gt; 普通文本」的优先级施加奖惩。
 */
public final class AmlLineBalancer
{
	/** 单个词就超过容器宽度时的大惩罚倍数。 */
	static final double OVERFLOW_PENALTY_MULTIPLIER = 1000;
	/** 截断 CJK 词组边界的惩罚比例。 */
	static final double CJK_BREAK_PENALTY_RATIO = 0.15;
	/** 截断普通文本的惩罚比例。 */
	static final double NORMAL_BREAK_PENALTY_RATIO = 0.5;
	/** 在空格处断开的奖励比例。 */
	static final double SPACE_BREAK_REWARD_RATIO = 0.4;
	/** 在标点处断开的奖励比例，高于空格以优先在标点换行。 */
	static final double PUNCTUATION_BREAK_REWARD_RATIO = 0.6;

	private AmlLineBalancer()
	{}

	/**
	 * 参与平衡排版的一个片段。
	 *
	 * @param text 片段文本
	 * @param width 片段渲染宽度
	 * @param space 片段是否全为空白
	 * @param cjkBoundary 片段是否是一个 CJK 词组的起点
	 */
	public record Token(String text, double width, boolean space,
		boolean cjkBoundary)
	{}

	/**
	 * 计算换行点，返回值是「需要另起一行的片段下标」升序数组。
	 *
	 * <p>返回空数组表示无需换行。</p>
	 */
	public static int[] breaks(Token[] tokens, double containerWidth)
	{
		int n = tokens == null ? 0 : tokens.length;
		if(n == 0 || containerWidth <= 0)
			return new int[0];

		// 字符偏移量与前缀宽度，便于 O(1) 查询
		int[] charOffsets = new int[n + 1];
		double[] prefixWidth = new double[n + 1];
		for(int i = 0; i < n; i++)
		{
			charOffsets[i + 1] = charOffsets[i] + tokens[i].text().length();
			prefixWidth[i + 1] = prefixWidth[i] + tokens[i].width();
		}

		if(prefixWidth[n] <= containerWidth)
			return new int[0];

		// CJK 词界集合（以字符偏移表示）
		java.util.Set<Integer> cjkBoundaries = new java.util.HashSet<>();
		for(int i = 1; i < n; i++)
			if(tokens[i].cjkBoundary())
				cjkBoundaries.add(charOffsets[i]);

		double[] dp = new double[n + 1];
		int[] nextBreak = new int[n + 1];
		java.util.Arrays.fill(dp, Double.POSITIVE_INFINITY);
		java.util.Arrays.fill(nextBreak, -1);
		dp[n] = 0;

		double penaltyCjk = Math.pow(containerWidth * CJK_BREAK_PENALTY_RATIO, 2);
		double penaltyNormal = Math
			.pow(containerWidth * NORMAL_BREAK_PENALTY_RATIO, 2);

		for(int i = n - 1; i >= 0; i--)
		{
			for(int j = i + 1; j <= n; j++)
			{
				double w = prefixWidth[j] - prefixWidth[i];
				double lineCost;

				if(w > containerWidth)
				{
					if(j == i + 1)
						// 单个无法分割的片段自身就比容器宽，被迫独立成行
						lineCost = Math.pow(w - containerWidth, 2)
							* OVERFLOW_PENALTY_MULTIPLIER;
					else
						// 行内含多个超宽片段，跳过该切分
						continue;
				}else
					// 迫使所有行长度方差最小
					lineCost = Math.pow(containerWidth - w, 2);

				double breakPenalty = 0;
				if(j < n)
				{
					Token previous = tokens[j - 1];
					if(isPunctuationEnd(previous.text()))
						breakPenalty = -Math
							.pow(containerWidth * PUNCTUATION_BREAK_REWARD_RATIO, 2);
					else if(previous.space())
						breakPenalty = -Math
							.pow(containerWidth * SPACE_BREAK_REWARD_RATIO, 2);
					else if(cjkBoundaries.contains(charOffsets[j]))
						breakPenalty = penaltyCjk;
					else
						breakPenalty = penaltyNormal;
				}

				double total = lineCost + breakPenalty + dp[j];
				if(total < dp[i])
				{
					dp[i] = total;
					nextBreak[i] = j;
				}
			}
		}

		java.util.List<Integer> result = new java.util.ArrayList<>();
		int current = 0;
		while(current < n)
		{
			current = nextBreak[current];
			if(current <= 0)
				break;
			if(current < n)
				result.add(current);
		}

		int[] breaks = new int[result.size()];
		for(int i = 0; i < breaks.length; i++)
			breaks[i] = result.get(i);
		return breaks;
	}

	/** 与 AMLL 的 {@code PUNCTUATION_REGEX} 一致：片段以标点结尾。 */
	static boolean isPunctuationEnd(String text)
	{
		if(text == null || text.isEmpty())
			return false;
		char last = text.charAt(text.length() - 1);
		return ",.;:!?，。；：！？、）】》」』’”)]}>~…".indexOf(last) >= 0;
	}
}
