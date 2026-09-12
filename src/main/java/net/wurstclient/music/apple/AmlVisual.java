package net.wurstclient.music.apple;

/**
 * applemusic-like-lyrics {@code lyric-player/base/index.ts} 中
 * {@code resolveIsActive} / {@code resolveOpacity} / {@code resolveBlurLevel}
 * 与 {@code base/group.ts} 的缩放常量的逐条移植。
 */
public final class AmlVisual
{
	/** 高亮行目标透明度（{@code resolveOpacity}）。 */
	public static final double HIGHLIGHTED_OPACITY = 0.85;
	/** 逐行歌词（非逐字）的未高亮透明度。 */
	public static final double NON_DYNAMIC_OPACITY = 0.2;
	/** {@code hidePassedLines} 时已播放行使用的极小值。 */
	public static final double HIDDEN_PASSED_OPACITY = 1e-4;
	/** 非激活主歌词行缩放（百分比）。 */
	public static final double INACTIVE_SCALE = 97;
	/** 非激活背景人声行缩放（百分比）。 */
	public static final double INACTIVE_BG_SCALE = 75;
	/** 模糊上限（px），对应 {@code Math.min(5, blur)}。 */
	public static final double MAX_BLUR_PX = 5;
	/** 视口外行的模糊档位。 */
	public static final double OUT_OF_VIEW_BLUR = 5;
	/** 窄视口模糊折扣（{@code window.innerWidth <= 1024}）。 */
	public static final double NARROW_VIEWPORT_BLUR_SCALE = 0.8;
	public static final int NARROW_VIEWPORT_WIDTH = 1024;
	/** 逐字渐变遮罩宽度 = 字高 × 该系数。 */
	public static final double WORD_FADE_WIDTH = 0.5;
	/**
	 * 非激活行（SOLID 渲染模式）的遮罩 alpha。
	 *
	 * <p>CSS 里 {@code .lyricLine} 的 {@code --bright-mask-alpha} 与
	 * {@code --dark-mask-alpha} 都是 0.2，渐变两端同值即整行均匀 0.2。这才是
	 * 非激活行显暗的真正来源——{@code resolveOpacity} 对逐字歌词返回 1，
	 * 是因为这一层由遮罩补上；逐行歌词没有词元素、不套遮罩，才由 opacity 返回 0.2。</p>
	 */
	public static final double SOLID_MASK_ALPHA = 0.2;
	/** 激活行（GRADIENT 渲染模式）已唱部分的遮罩 alpha。 */
	public static final double ACTIVE_BRIGHT_MASK_ALPHA = 1.0;
	/** 激活行未唱部分的遮罩 alpha，对应 {@code --dark-mask-alpha: 0.4}。 */
	public static final double ACTIVE_DARK_MASK_ALPHA = 0.4;
	/** 遮罩 alpha 切到激活态的过渡时长（{@code --mask-alpha-duration: 0.3s}）。 */
	public static final double MASK_ACTIVATE_SECONDS = 0.3;
	/** 遮罩 alpha 退回非激活态的过渡时长（默认 {@code 0.45s}）。 */
	public static final double MASK_DEACTIVATE_SECONDS = 0.45;
	/** {@code .lyricLineWrapper} 的 {@code opacity 0.4s ease}。 */
	public static final double OPACITY_TRANSITION_SECONDS = 0.4;
	/** {@code .lyricLineWrapper} 的 {@code filter 0.4s ease}。 */
	public static final double BLUR_TRANSITION_SECONDS = 0.4;
	/** 由 {@link #maskEdges} 返回的亮部边界下标。 */
	public static final int MASK_BRIGHT_EDGE = 0;
	/** 由 {@link #maskEdges} 返回的暗部边界下标。 */
	public static final int MASK_DARK_EDGE = 1;

	/**
	 * 逐字遮罩渐变的精确几何，返回 {@code {亮部边界, 暗部边界}}（元素局部坐标，px）。
	 *
	 * <p>AMLL 用的是「一张两端带渐变的遮罩图 + 平移」而不是「把渐变居中在交界处」。
	 * {@code generateFadeGradient} 产出 {@code totalAspect = 2 + r}（{@code r =
	 * fadeWidth / C}）宽度的遮罩图，亮部停靠点在 {@code leftPos = (1 -
	 * widthInTotal) / 2}、暗部停靠点在 {@code leftPos + widthInTotal}；再按
	 * {@code maskSize = totalAspect × 100%} 缩放后，两个停靠点落在元素坐标
	 * {@code C} 与 {@code C + fadeWidth}。遮罩位置则从 {@code -(C + fadeWidth)}
	 * 线性走到 {@code 0}，于是：</p>
	 *
	 * <pre>
	 * 亮部边界 = C + maskPos
	 * 暗部边界 = C + fadeWidth + maskPos
	 * </pre>
	 *
	 * <p>关键差别：渐变**不在交界处居中**，而是整段位于亮部边界的右侧。两者只在
	 * progress = 0.5 时重合，两端各差 {@code fadeWidth / 2}。</p>
	 *
	 * @param wordWidth 词元素宽度 C（px）
	 * @param fadeWidth 渐变宽度 = 字高 × {@link #WORD_FADE_WIDTH}（px）
	 * @param progress  词的时间进度，会被夹到 {@code [0, 1]}
	 */
	public static double[] maskEdges(double wordWidth, double fadeWidth,
		double progress)
	{
		double c = Math.max(0, wordWidth);
		double f = Math.max(0.0001, fadeWidth);
		double p = clamp01(progress);
		double w = c + f;
		double maskPos = Math.max(-w, Math.min(0, -w + p * w));
		double brightEdge = c + maskPos;
		return new double[]{brightEdge, brightEdge + f};
	}

	/** 由 {@link #maskEdges} 推出的元素内某点的遮罩 alpha。 */
	public static double maskAlphaAt(double x, double brightEdge,
		double fadeWidth, double bright, double dark)
	{
		double f = Math.max(0.0001, fadeWidth);
		double t = clamp01((x - brightEdge) / f);
		return bright + (dark - bright) * t;
	}

	/** 背景人声行 CSS 缩放与透明度。 */
	public static final double BG_LINE_SCALE = 0.7;
	public static final double BG_LINE_OPACITY = 0.4;
	/** 翻译 / 音译行 CSS 缩放与透明度。 */
	public static final double SUB_LINE_SCALE = 0.5;
	public static final double SUB_LINE_OPACITY = 0.3;
	/** 间奏点上下外边距 = 字号 × 该系数。 */
	public static final double INTERLUDE_DOT_MARGIN_EM = 0.4;
	/** 间奏点左侧内边距 {@code padding: 2.5% 0.75em} 的 em 部分。 */
	public static final double INTERLUDE_DOT_PADDING_EM = 0.75;
	/** 歌词行左右内边距 {@code --lyric-line-padding-x: 1em}。 */
	public static final double LINE_PADDING_EM = 1.0;
	/** 行内上浮幅度（em），背景行翻倍。 */
	public static final double FLOAT_EM = 0.05;
	/** 默认字号，对应 AMLL {@code baseFontSize || 24}。 */
	public static final double DEFAULT_FONT_SIZE = 24;

	private AmlVisual()
	{}

	/** {@code resolveIsActive}。 */
	public static boolean isActive(boolean highlighted, int index,
		int scrollToIndex, int latestIndex)
	{
		return highlighted
			|| index >= scrollToIndex && index < latestIndex;
	}

	/** {@code resolveOpacity}。 */
	public static double opacity(boolean inViewport, boolean highlighted,
		boolean nonDynamic, boolean hidePassedLines, boolean playing, int index,
		int passedBoundary)
	{
		if(!inViewport)
			return 0;
		if(hidePassedLines && playing && index < passedBoundary)
			return HIDDEN_PASSED_OPACITY;
		if(highlighted)
			return HIGHLIGHTED_OPACITY;
		return nonDynamic ? NON_DYNAMIC_OPACITY : 1;
	}

	/** {@code resolveBlurLevel}。 */
	public static double blurLevel(int index, boolean focused,
		boolean inViewport, boolean touchScrolled, boolean enableBlur,
		int scrollToIndex, int latestIndex, boolean narrowViewport)
	{
		if(!enableBlur)
			return 0;
		if(!inViewport)
			return OUT_OF_VIEW_BLUR;
		if(touchScrolled || focused)
			return 0;
		int distance = index < scrollToIndex
			? Math.abs(scrollToIndex - index) + 1
			: Math.abs(index - latestIndex);
		double level = 1 + distance;
		return narrowViewport ? level * NARROW_VIEWPORT_BLUR_SCALE : level;
	}

	/** {@code Math.min(5, blur)}。 */
	public static double blurPx(double level)
	{
		return Math.min(MAX_BLUR_PX, level);
	}

	/**
	 * {@code setLineTransformations}：主歌词非激活 97、背景人声非激活 75，
	 * 暂停或激活时为 100。
	 */
	public static double mainScale(boolean isActive, boolean playing)
	{
		return !isActive && playing ? INACTIVE_SCALE : 100;
	}

	public static double backgroundScale(boolean isActive, boolean playing)
	{
		return !isActive && playing ? INACTIVE_BG_SCALE : 100;
	}

	/**
	 * 背景人声滑入偏移：激活或暂停时归零，否则按方向藏到 ±80。
	 */
	public static double backgroundSlideTarget(boolean isActive,
		boolean playing, boolean backgroundFirst)
	{
		if(isActive || !playing)
			return 0;
		return backgroundFirst ? 80 : -80;
	}

	/** {@code bgWrapper} 的 activeProgress → scale（0.8 + progress * 0.2）。 */
	public static double backgroundActiveScale(double slideY)
	{
		double progress = clamp01(1 - Math.abs(slideY) / 80);
		return 0.8 + progress * 0.2;
	}

	/**
	 * 遮罩 alpha 的目标值。
	 *
	 * @param active 该行是否激活（激活即 GRADIENT 渲染模式）
	 * @return {@code [bright, dark]}
	 */
	public static double[] maskAlphaTargets(boolean active)
	{
		return active
			? new double[]{ACTIVE_BRIGHT_MASK_ALPHA, ACTIVE_DARK_MASK_ALPHA}
			: new double[]{SOLID_MASK_ALPHA, SOLID_MASK_ALPHA};
	}

	/** 遮罩 alpha 过渡时长：切到激活用 0.3s，退回用 0.45s。 */
	public static double maskTransitionSeconds(boolean active)
	{
		return active ? MASK_ACTIVATE_SECONDS : MASK_DEACTIVATE_SECONDS;
	}

	/** 背景人声行是否向上前置（BG 起始早于主歌词）。 */
	public static boolean backgroundFirst(long bgStartMs, long mainStartMs)
	{
		return bgStartMs < mainStartMs;
	}

	static double clamp01(double value)
	{
		return Math.max(0, Math.min(1, value));
	}
}
