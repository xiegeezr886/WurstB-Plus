package net.wurstclient.music.apple;

import net.wurstclient.music.LyricWord;

/**
 * applemusic-like-lyrics {@code lyric-player/dom/lyric-line.ts} 中
 * {@code LyricLineBase.shouldEmphasize}、{@code initFloatAnimation} 与
 * {@code initEmphasizeAnimation} 的移植。
 *
 * <p>每个词都有一次 ease-out 上浮（背景人声翻倍）；满足强调条件的词再叠加逐字
 * 缩放、左右位移、上下位移与白色辉光，辉光强度由 32 帧 {@link AmlEasing#empEasing}
 * 驱动。</p>
 */
public final class AmlEmphasize
{
	/** 关键帧数量，对应 {@code ANIMATION_FRAME_QUANTITY}。 */
	public static final int FRAMES = 32;
	private static final double AMOUNT_CAP = 1.2;
	private static final double BLUR_CAP = 0.8;
	private static final double GLOW_BLUR_CAP_EM = 0.3;

	private AmlEmphasize()
	{}

	/** {@code LyricLineBase.shouldEmphasize}。 */
	public static boolean shouldEmphasize(LyricWord word)
	{
		if(word == null)
			return false;
		long duration = word.durationMs();
		if(isCjkWord(word.text()))
			return duration >= 1000;
		String trimmed = word.text().trim();
		return duration >= 1000 && trimmed.length() <= 7
			&& trimmed.length() > 1;
	}

	/** 整词皆为 CJK 统一表意文字，对应 AMLL {@code isCJK(word)}。 */
	public static boolean isCjkWord(String text)
	{
		if(text == null || text.isEmpty())
			return false;
		for(int i = 0; i < text.length(); i++)
			if(!LyricWordSplitter.isCJK(text.charAt(i)))
				return false;
		return true;
	}

	/**
	 * 单个词的动画描述：基础上浮 + 可选的逐字强调。
	 *
	 * @param lineStartMs 行起始时间，用于把绝对时间换算成行内相对时间
	 * @param lastWordOfLine 该词是否包含本行最后一个词（AMLL 用它放大强调幅度）
	 */
	public static Word word(LyricWord word, long lineStartMs,
		boolean lastWordOfLine, boolean background, int charCount)
	{
		double up = AmlVisual.FLOAT_EM * (background ? 2 : 1);
		long floatDelay = word.startMs() - lineStartMs;
		double floatDuration = Math.max(1000, word.durationMs());

		boolean emphasize = shouldEmphasize(word);
		double duration = Math.max(1000, word.durationMs());
		double delay = Math.max(0, floatDelay);

		double amount = duration / 2000;
		amount = amount > 1 ? Math.sqrt(amount) : Math.pow(amount, 3);
		double blur = duration / 3000;
		blur = blur > 1 ? Math.sqrt(blur) : Math.pow(blur, 3);
		amount *= 0.6;
		blur *= 0.5;
		if(lastWordOfLine)
		{
			amount *= 1.6;
			blur *= 1.5;
			duration *= 1.2;
		}
		amount = Math.min(AMOUNT_CAP, amount);
		blur = Math.min(BLUR_CAP, blur);

		return new Word(up, floatDelay, floatDuration, emphasize, amount, blur,
			duration, delay, Math.max(1, charCount), background);
	}

	/** 逐字强调与基础上浮的求值结果。 */
	public record Frame(double scale, double offsetXEm, double offsetYEm,
		double glowAlpha, double glowBlurEm)
	{}

	/**
	 * 求某个字符在行内相对时间下的强调结果（含基础上浮与强调附加上浮）。
	 *
	 * @param word 词动画描述
	 * @param charIndex 字符序号
	 * @param lineRelativeMs 当前播放时间 − 行起始时间
	 */
	public static Frame evaluate(Word word, int charIndex,
		long lineRelativeMs)
	{
		double baseFloatY = -word.upEm() * AmlEasing
			.easeOut(progress(lineRelativeMs, word.floatDelayMs(),
				word.floatDurationMs()));

		if(!word.emphasize())
			return new Frame(1, 0, baseFloatY, 0, 0);

		double charDelay = word.delayMs()
			+ word.durationMs() / 2.5 / word.charCount() * charIndex;
		double x = progress(lineRelativeMs, charDelay, word.durationMs());
		double transX = AmlEasing.empEasing(x);

		double scale = 1 + transX * 0.1 * word.amount();
		double offsetX = -transX * 0.03 * word.amount()
			* (word.charCount() / 2.0 - charIndex);
		double offsetY = -transX * 0.025 * word.amount();
		double glow = transX * word.glowBlur();

		// 强调词额外叠加一次正弦上浮（composite: add），背景人声翻倍
		double empFloat = -Math.sin(x * Math.PI) * AmlVisual.FLOAT_EM
			* (word.background() ? 2 : 1);

		return new Frame(scale, offsetX, offsetY + baseFloatY + empFloat, glow,
			Math.min(GLOW_BLUR_CAP_EM, word.glowBlur() * 0.3));
	}

	private static double progress(long timeMs, double delayMs,
		double durationMs)
	{
		if(durationMs <= 0)
			return timeMs >= delayMs ? 1 : 0;
		double t = (timeMs - delayMs) / durationMs;
		return Math.max(0, Math.min(1, t));
	}

	/**
	 * 单个词的动画参数，字段对应 AMLL {@code initFloatAnimation} 与
	 * {@code initEmphasizeAnimation} 内部推导出的量。
	 */
	public record Word(double upEm, long floatDelayMs, double floatDurationMs,
		boolean emphasize, double amount, double glowBlur, double durationMs,
		double delayMs, int charCount, boolean background)
	{}
}
