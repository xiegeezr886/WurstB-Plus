package net.wurstclient.music.apple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.wurstclient.clickgui2.PingFangFont;
import net.wurstclient.music.LyricLine;
import net.wurstclient.music.LyricRuby;
import net.wurstclient.music.LyricWord;

/**
 * applemusic-like-lyrics 歌词舞台的 Minecraft 移植（视觉一比一）。
 *
 * <p>对齐 {@code packages/core} 的视觉规则：</p>
 * <ul>
 * <li>主歌词统一 1em 字号，非激活行只经弹簧缩放到 97%，背景人声 75%；</li>
 * <li>透明度按 {@code resolveOpacity}；非激活行的「暗」由遮罩在 SOLID 模式下
 * 均匀压到 0.2 提供，因此逐字歌词的 opacity 反而是 1；</li>
 * <li>模糊按 {@code resolveBlurLevel}：1 + 距焦点行数，窄视口 ×0.8，上限 5px；</li>
 * <li>遮罩 alpha、opacity、模糊都按 CSS 过渡 0.3 / 0.45 / 0.4 秒缓动；</li>
 * <li>每个词一次 ease-out 上浮，强调词叠加逐字缩放／位移／辉光；</li>
 * <li>换行点由 {@link AmlLineBalancer} 动态规划挑选，避免末行过短。</li>
 * </ul>
 */
public final class AppleLyricPlayer
{
	public static final class LineRender
	{
		public double y;
		public boolean isInViewport;
		public boolean isActive;
		public double opacity;
		public double blurLevel;
		public String text;
		public String translation;
		public String romanization;
		public boolean background;
		public boolean hasRuby;
	}

	private static final double POS_Y_MASS = 0.9;
	private static final double POS_Y_DAMPING = 15;
	private static final double POS_Y_STIFFNESS = 90;
	private static final double SCALE_MASS = 2;
	private static final double SCALE_DAMPING = 25;
	private static final double SCALE_STIFFNESS = 100;
	private static final double BG_SCALE_MASS = 1;
	private static final double BG_SCALE_DAMPING = 20;
	private static final double BG_SCALE_STIFFNESS = 50;
	private static final double SLOW_STIFFNESS = 90;
	private static final double SLOW_DAMPING = 15;
	private static final double MIN_INTERVAL = 100;
	private static final double MAX_INTERVAL = 800;
	private static final double MIN_STIFFNESS = 170;
	private static final double MAX_STIFFNESS = 220;
	private static final double DAMPING_MULTIPLIER = 2.2;
	private static final double INTERVAL_EXPONENT = 0.2;
	private static final long AUTO_ALIGN_RESUME_MS = 5_000;
	/** 行高相对字号的比例（CSS line-height 近似）。 */
	private static final double ROW_LEADING = 1.2;
	/** Minecraft 字体基准字形高度。 */
	private static final float MC_GLYPH = 9F;
	/** MC 字体路径高斯模糊的采样上限。 */
	private static final int MAX_BLUR_TAPS = 16;
	/** 低于该权重的采样点丢弃。 */
	private static final double MIN_BLUR_TAP_WEIGHT = 0.02;
	/** 生效的最小模糊半径，低于此值直接单次绘制。 */
	private static final double MIN_BLUR_PX = 0.5;
	/** 高斯采样表缓存：量化后的 sigma → 采样点。 */
	private static final Map<Integer, double[][]> BLUR_TAP_CACHE = new HashMap<>();

	private final AppleTimeline timeline = new AppleTimeline();
	private final AppleLayout layout = new AppleLayout();
	private final InterludeDots interludeDots = new InterludeDots();

	private List<LyricLine> rawLines = List.of();
	private AmlMask.Mode maskMode = AmlMask.Mode.DISABLED;
	private char maskChar = AmlMask.DEFAULT_MASK_CHAR;
	private boolean mcBlurEnabled = true;

	private Spring[] posYSprings = new Spring[0];
	private Spring[] bgSlideSprings = new Spring[0];
	private Spring[] mainScaleSprings = new Spring[0];
	private Spring[] bgScaleSprings = new Spring[0];
	private AmlTween[] opacityTweens = new AmlTween[0];
	private AmlTween[] blurTweens = new AmlTween[0];
	private AmlTween[] brightMaskTweens = new AmlTween[0];
	private AmlTween[] darkMaskTweens = new AmlTween[0];
	private LineRender[] renders = new LineRender[0];
	private LyricWord[][] words = new LyricWord[0][];
	private AmlEmphasize.Word[][] wordAnims = new AmlEmphasize.Word[0][];
	private int[][] lineBreaks = new int[0][];

	private long[] starts = new long[0];
	/**
	 * 解析器给出的原始行起始时间。
	 *
	 * <p>{@link AmlOptimize#optimize} 会按 AMLL 的 {@code tryAdvanceStartTime}
	 * 把行起始时间提前最多 600ms 用于高亮，但拖动进度条仍应回到歌词原本的时间点，
	 * 因此这里单独保留一份原始值。</p>
	 */
	private long[] rawStarts = new long[0];
	private long[] ends = new long[0];
	private String[] texts = new String[0];
	private String[] translations = new String[0];
	private String[] romanizations = new String[0];
	private boolean[] backgrounds = new boolean[0];
	private boolean[] hasRuby = new boolean[0];

	private int lineCount;
	private double containerHeight;
	private double contentWidth;
	private double fontSize = AmlVisual.DEFAULT_FONT_SIZE;
	private boolean nonDynamic;
	private boolean heightsDirty = true;
	private double scrollOffset;
	private boolean autoAlignSuspended;
	private boolean touchScrolled;
	private long lastInteractionNanos;
	private boolean playing = true;
	private boolean enableBlur = true;
	private boolean enableScale = true;
	private boolean hidePassedLines;
	private boolean endOfSong;
	private long lastUpdateNanos = System.nanoTime();
	private long lastInterludeEnd = Long.MIN_VALUE;

	// ---------- 数据装载 ----------

	public void setLyricLines(List<LyricLine> lines, long initialTime)
	{
		rawLines = lines == null ? List.of() : List.copyOf(lines);
		rebuildLines(initialTime);
	}

	/** 设置不雅词掩码模式；会重跑一遍歌词流水线。 */
	public void setMaskMode(AmlMask.Mode mode)
	{
		AmlMask.Mode next = mode == null ? AmlMask.Mode.DISABLED : mode;
		if(maskMode == next)
			return;
		maskMode = next;
		rebuildLines(timeline.getCurrentTime());
	}

	public void setMaskChar(char value)
	{
		if(maskChar == value)
			return;
		maskChar = value;
		rebuildLines(timeline.getCurrentTime());
	}

	public AmlMask.Mode getMaskMode()
	{
		return maskMode;
	}

	private void rebuildLines(long initialTime)
	{
		// AMLL 的顺序是「先优化、后掩码」：掩码会改变字形宽度，必须在测量之前完成
		List<LyricLine> processed = AmlOptimize
			.optimize(AmlMask.apply(rawLines, maskMode, maskChar));
		lineCount = processed.size();
		long[] sortedRaw = rawLines.stream().mapToLong(LyricLine::timeMs)
			.sorted().toArray();
		rawStarts = sortedRaw.length == lineCount ? sortedRaw : null;

		starts = new long[lineCount];
		ends = new long[lineCount];
		texts = new String[lineCount];
		translations = new String[lineCount];
		romanizations = new String[lineCount];
		backgrounds = new boolean[lineCount];
		hasRuby = new boolean[lineCount];
		words = new LyricWord[lineCount][];
		wordAnims = new AmlEmphasize.Word[lineCount][];
		lineBreaks = new int[lineCount][];
		nonDynamic = AmlOptimize.isNonDynamic(processed);

		for(int i = 0; i < lineCount; i++)
		{
			LyricLine line = processed.get(i);
			starts[i] = line.timeMs();
			long fallback = i + 1 < lineCount ? processed.get(i + 1).timeMs()
				: line.timeMs() + 8_000;
			ends[i] = Math.max(line.endMs(fallback), starts[i] + 1);
			texts[i] = line.text();
			translations[i] = line.translatedLyric();
			romanizations[i] = line.romanLyric();
			backgrounds[i] = line.background();
			hasRuby[i] = line.hasRuby();
			words[i] = buildWords(line, starts[i], ends[i]);
			wordAnims[i] = buildAnims(words[i], starts[i], backgrounds[i]);
		}

		// AMLL 把新歌词行的 posY 初值设在容器高两倍处，载入时靠弹簧从下方飞入
		double flyInStart = (containerHeight > 0 ? containerHeight : 240) * 2;
		posYSprings = new Spring[lineCount];
		bgSlideSprings = new Spring[lineCount];
		mainScaleSprings = new Spring[lineCount];
		bgScaleSprings = new Spring[lineCount];
		opacityTweens = new AmlTween[lineCount];
		blurTweens = new AmlTween[lineCount];
		brightMaskTweens = new AmlTween[lineCount];
		darkMaskTweens = new AmlTween[lineCount];
		renders = new LineRender[lineCount];
		for(int i = 0; i < lineCount; i++)
		{
			posYSprings[i] = new Spring(flyInStart);
			posYSprings[i].updateParams(POS_Y_STIFFNESS, POS_Y_DAMPING,
				POS_Y_MASS, false);
			bgSlideSprings[i] = new Spring(backgrounds[i] ? -80 : 0);
			bgSlideSprings[i].updateParams(POS_Y_STIFFNESS, POS_Y_DAMPING,
				POS_Y_MASS, false);
			mainScaleSprings[i] = new Spring(100);
			mainScaleSprings[i].updateParams(SCALE_STIFFNESS, SCALE_DAMPING,
				SCALE_MASS, false);
			bgScaleSprings[i] = new Spring(100);
			bgScaleSprings[i].updateParams(BG_SCALE_STIFFNESS,
				BG_SCALE_DAMPING, BG_SCALE_MASS, false);
			opacityTweens[i] = new AmlTween(0, false);
			blurTweens[i] = new AmlTween(0, false);
			brightMaskTweens[i] = new AmlTween(AmlVisual.SOLID_MASK_ALPHA, true);
			darkMaskTweens[i] = new AmlTween(AmlVisual.SOLID_MASK_ALPHA, true);
			renders[i] = new LineRender();
		}

		heightsDirty = true;
		rebuildView(initialTime);
	}

	/**
	 * 没有逐字时间戳时按 AMLL {@code chunkAndSplitLyricWords} 的思路按字宽切分，
	 * 仅用于测量与换行；逐行歌词不套卡拉 OK 遮罩，因此这些时间戳不会被消费。
	 */
	private static LyricWord[] buildWords(LyricLine line, long start, long end)
	{
		if(line.hasWordTimings())
			return line.words().toArray(LyricWord[]::new);
		if(line.text().isEmpty())
			return new LyricWord[0];
		List<String> pieces = LyricWordSplitter.split(line.text());
		if(pieces.isEmpty())
			return new LyricWord[]{new LyricWord(line.text(), start, end)};
		List<String> merged = new ArrayList<>(pieces.size());
		for(int i = 0; i < pieces.size(); i++)
		{
			String piece = pieces.get(i);
			if(i > 0 && !piece.isEmpty()
				&& !LyricWordSplitter.isCJK(piece.charAt(0)))
				merged.add(" " + piece);
			else
				merged.add(piece);
		}
		float[][] fractions = LyricWordSplitter.wordFractions(merged);
		long duration = Math.max(1, end - start);
		LyricWord[] timed = new LyricWord[merged.size()];
		for(int i = 0; i < merged.size(); i++)
			timed[i] = new LyricWord(merged.get(i),
				start + Math.round(duration * fractions[i][0]),
				start + Math.round(duration * fractions[i][1]));
		return timed;
	}

	private static AmlEmphasize.Word[] buildAnims(LyricWord[] lineWords,
		long lineStart, boolean background)
	{
		AmlEmphasize.Word[] anims = new AmlEmphasize.Word[lineWords.length];
		String lastWordText = lineWords.length == 0 ? ""
			: lineWords[lineWords.length - 1].text();
		for(int w = 0; w < lineWords.length; w++)
		{
			LyricWord word = lineWords[w];
			boolean last = !lastWordText.isEmpty()
				&& word.text().contains(lastWordText);
			int anchors = word.hasRuby() ? word.rubyCharCount()
				: graphemeCount(word.text());
			anims[w] = AmlEmphasize.word(word, lineStart, last, background,
				anchors);
		}
		return anims;
	}

	private static int graphemeCount(String text)
	{
		return text == null || text.isEmpty() ? 1 : text.codePointCount(0,
			text.length());
	}

	private void rebuildView(long initialTime)
	{
		timeline.setTimeBounds(starts, ends);
		layout.initHeights(lineCount, defaultLineHeight());
		scrollOffset = 0;
		autoAlignSuspended = false;
		touchScrolled = false;
		interludeDots.clear();
		lastInterludeEnd = Long.MIN_VALUE;
		setCurrentTime(initialTime, true);
	}

	private double defaultLineHeight()
	{
		return containerHeight > 0 ? containerHeight / 5
			: fontSize * ROW_LEADING;
	}

	// ---------- 尺寸与配置 ----------

	public void setContainerHeight(double height)
	{
		boolean changed = Math.abs(containerHeight - height) > 0.5;
		containerHeight = height;
		if(changed && lineCount > 0)
			calcLayout(AmlLayoutReason.RESIZE);
	}

	public void setContentWidth(double width)
	{
		boolean changed = Math.abs(contentWidth - width) > 0.5;
		contentWidth = width;
		if(changed)
		{
			heightsDirty = true;
			if(lineCount > 0)
				calcLayout(AmlLayoutReason.RESIZE);
		}
	}

	/** 设置基准字号（1em），默认 24，对应 AMLL {@code baseFontSize}。 */
	public void setFontSize(double size)
	{
		double next = Math.max(8, size);
		if(Math.abs(fontSize - next) < 0.01)
			return;
		fontSize = next;
		heightsDirty = true;
		if(lineCount > 0)
			calcLayout(AmlLayoutReason.CONFIG_CHANGE);
	}

	public double getFontSize()
	{
		return fontSize;
	}

	/**
	 * MC 字体路径是否用高斯采样近似模糊。
	 *
	 * <p>需要多次重绘文字，字号大、可见行多时开销可观；若掉帧可关闭，
	 * 关闭后退化为按字重区分层次。</p>
	 */
	public void setMcBlurEnabled(boolean value)
	{
		mcBlurEnabled = value;
	}

	public void setPlaying(boolean value)
	{
		if(playing == value)
			return;
		playing = value;
		if(lineCount > 0)
			calcLayout(AmlLayoutReason.CONFIG_CHANGE);
	}

	public void setEnableBlur(boolean value)
	{
		if(enableBlur == value)
			return;
		enableBlur = value;
		if(lineCount > 0)
			calcLayout(AmlLayoutReason.CONFIG_CHANGE);
	}

	public void setEnableScale(boolean value)
	{
		if(enableScale == value)
			return;
		enableScale = value;
		if(lineCount > 0)
			calcLayout(AmlLayoutReason.CONFIG_CHANGE);
	}

	public void setHidePassedLines(boolean value)
	{
		if(hidePassedLines == value)
			return;
		hidePassedLines = value;
		if(lineCount > 0)
			calcLayout(AmlLayoutReason.CONFIG_CHANGE);
	}

	public void setCurrentTime(long time, boolean seek)
	{
		measureHeights();
		timeline.sync(time, seek);
		if(!timeline.hasChanged())
			return;
		boolean timeJumped = timeline.isTimeJumped();
		endOfSong = timeline.isEndOfSong();
		if(timeJumped && !touchScrolled)
			resetScroll();
		if(timeline.isInterludeChanged() || timeline.isScrollToChanged()
			|| timeJumped)
			updateSpringParams(timeline.activeInterlude() != -1, timeJumped);
		calcLayout(seek || timeJumped ? AmlLayoutReason.SEEK
			: AmlLayoutReason.PLAYBACK_TICK);
	}

	public long getCurrentTime()
	{
		return timeline.getCurrentTime();
	}

	/** 滚轮等离散单步滚动，对应 {@code LayoutReason.DiscreteScroll}。 */
	public void scroll(double delta)
	{
		scroll(delta, false);
	}

	/**
	 * 拖动或惯性滑动，对应 {@code LayoutReason.ContinuousScroll}。
	 *
	 * <p>连续滚动会瞬移 Y 轴，否则弹簧会让拖动不跟手。</p>
	 */
	public void scroll(double delta, boolean continuous)
	{
		scrollOffset = clamp(scrollOffset - delta * 28, layout.minOffset(),
			layout.maxOffset());
		boolean wasSuspended = autoAlignSuspended;
		autoAlignSuspended = true;
		touchScrolled = true;
		lastInteractionNanos = System.nanoTime();
		calcLayout(continuous || wasSuspended
			? AmlLayoutReason.CONTINUOUS_SCROLL
			: AmlLayoutReason.INTERACTION_START);
	}

	public void resetScroll()
	{
		scrollOffset = 0;
		autoAlignSuspended = false;
		touchScrolled = false;
	}

	public int hitLine(double mouseY, double top, double bottom)
	{
		if(lineCount == 0 || mouseY < top || mouseY > bottom)
			return -1;
		for(int i = 0; i < lineCount; i++)
		{
			if(!renders[i].isInViewport)
				continue;
			double y = top + posYSprings[i].getCurrentPosition();
			double h = Math.max(layout.lineHeight(i), fontSize);
			if(mouseY >= y && mouseY < y + h)
				return i;
		}
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		for(int i = 0; i < lineCount; i++)
		{
			double y = top + posYSprings[i].getCurrentPosition()
				+ layout.lineHeight(i) / 2;
			double dist = Math.abs(y - mouseY);
			if(dist < bestDist)
			{
				bestDist = dist;
				best = i;
			}
		}
		return best;
	}

	public long lineTime(int index)
	{
		if(index < 0 || index >= lineCount)
			return 0;
		return rawStarts != null ? rawStarts[index] : starts[index];
	}

	public boolean isEmpty()
	{
		return lineCount == 0;
	}

	// ---------- 测量与排版 ----------

	private void measureHeights()
	{
		if(!heightsDirty || lineCount == 0)
			return;
		Font font = Minecraft.getInstance() == null ? null
			: Minecraft.getInstance().font;
		if(font == null)
			return;
		double padding = AmlVisual.LINE_PADDING_EM * fontSize;
		if(contentWidth <= padding * 2)
			return;
		double maxWidth = contentWidth - padding * 2;
		layout.initHeights(lineCount, defaultLineHeight());

		for(int i = 0; i < lineCount; i++)
		{
			double size = lineFontSize(i);
			Style weight = styleFor(i, false);
			lineBreaks[i] = balancedBreaks(font, i, weight, size, maxWidth);
			int rows = rowCount(i, font, weight, size, maxWidth);
			double height = rows * size * ROW_LEADING + rubyExtra(i, size);
			if(!translations[i].isBlank())
				height += subFontSize() * 1.5;
			if(!romanizations[i].isBlank())
				height += subFontSize() * 1.5;
			layout.setLineHeight(i, height);
		}
		heightsDirty = false;
	}

	/** 主歌词 1em、背景人声 {@code max(0.7em, 10px)}。 */
	private double lineFontSize(int index)
	{
		if(!backgrounds[index])
			return fontSize;
		return Math.max(AmlVisual.BG_LINE_SCALE * fontSize, 10);
	}

	private double subFontSize()
	{
		return Math.max(AmlVisual.SUB_LINE_SCALE * fontSize, 10);
	}

	/** 注音行高度：{@code font-size: 0.5em; line-height: 1em; min-height: 1em}。 */
	private double rubyExtra(int index, double size)
	{
		return hasRuby[index] ? size * AmlVisual.SUB_LINE_SCALE : 0;
	}

	private Style styleFor(int index, boolean active)
	{
		if(backgrounds[index])
			return PingFangFont.LIGHT_STYLE;
		if(active)
			return PingFangFont.SEMIBOLD_STYLE;
		boolean blurred = enableBlur && index < renders.length
			&& renders[index].blurLevel >= 3;
		return blurred ? PingFangFont.LIGHT_STYLE : PingFangFont.REGULAR_STYLE;
	}

	/** 用 {@link AmlLineBalancer} 求换行点；整体不超宽时返回空数组。 */
	private int[] balancedBreaks(Font font, int index, Style weight, double size,
		double maxWidth)
	{
		LyricWord[] lineWords = words[index];
		if(lineWords.length <= 1)
			return new int[0];
		double scale = size / MC_GLYPH;
		AmlLineBalancer.Token[] tokens = new AmlLineBalancer.Token[lineWords.length];
		for(int w = 0; w < lineWords.length; w++)
		{
			String text = lineWords[w].text();
			boolean space = text.trim().isEmpty();
			boolean cjkBoundary = !text.isEmpty()
				&& LyricWordSplitter.isCJK(text.charAt(0));
			tokens[w] = new AmlLineBalancer.Token(text,
				PingFangFont.width(font, text, weight) * scale, space,
				cjkBoundary);
		}
		return AmlLineBalancer.breaks(tokens, maxWidth);
	}

	private int rowCount(int index, Font font, Style weight, double size,
		double maxWidth)
	{
		LyricWord[] lineWords = words[index];
		if(lineWords.length == 0)
			return 1;
		double scale = size / MC_GLYPH;
		int[] breaks = lineBreaks[index];
		int breakCursor = 0;
		int rows = 1;
		double x = 0;
		for(int w = 0; w < lineWords.length; w++)
		{
			if(w > 0 && breakCursor < breaks.length && breaks[breakCursor] == w)
			{
				rows++;
				x = 0;
				breakCursor++;
			}
			double width = PingFangFont.width(font, lineWords[w].text(), weight)
				* scale;
			// 平衡器没给出断点（例如单个词就超宽）时退化为贪心换行
			if(breaks.length == 0 && x > 0 && x + width > maxWidth)
			{
				rows++;
				x = 0;
			}
			x += width;
		}
		return rows;
	}

	private void updateSpringParams(boolean interludeActive, boolean seeking)
	{
		int scrollTo = timeline.scrollToIndex();
		double stiffness;
		double damping;
		if(seeking || interludeActive || scrollTo <= 0)
		{
			stiffness = SLOW_STIFFNESS;
			damping = SLOW_DAMPING;
		}else
		{
			double interval = starts[scrollTo] - starts[scrollTo - 1];
			double clamped = Math.min(Math.max(interval, MIN_INTERVAL),
				MAX_INTERVAL);
			double ratio = 1 - (clamped - MIN_INTERVAL)
				/ (MAX_INTERVAL - MIN_INTERVAL);
			ratio = Math.pow(ratio, INTERVAL_EXPONENT);
			stiffness = MIN_STIFFNESS + ratio * (MAX_STIFFNESS - MIN_STIFFNESS);
			damping = Math.sqrt(stiffness) * DAMPING_MULTIPLIER;
		}
		for(Spring spring : posYSprings)
			spring.updateParams(stiffness, damping, POS_Y_MASS, false);
		for(Spring spring : bgSlideSprings)
			spring.updateParams(stiffness, damping, POS_Y_MASS, false);
	}

	/**
	 * 按 AMLL 的排版策略提交一帧布局。
	 *
	 * @param reason 触发原因，决定是否禁用阶梯交错、是否重置间奏点、是否瞬移
	 */
	private void calcLayout(AmlLayoutReason reason)
	{
		measureHeights();
		if(lineCount == 0)
			return;
		int scrollTo = timeline.scrollToIndex();
		int latest = timeline.latestHighlightedIndex();
		boolean interludeActive = timeline.activeInterlude() != -1;
		boolean focusInterlude = timeline.isFocusOnInterlude()
			&& interludeActive;

		int focalLine = scrollTo;
		int interludeAnchor = -1;
		double interludeHeight = 0;
		if(focusInterlude)
		{
			interludeAnchor = timeline.interludeAnchorLine();
			focalLine = Math.max(0, interludeAnchor);
			interludeHeight = interludeHeight();
		}else if(endOfSong)
			focalLine = lineCount - 1;
		// 焦点越界会让 layout 失去度量，把整段歌词画到容器顶部
		focalLine = Math.max(0, Math.min(focalLine, lineCount - 1));

		layout.beginFrame(containerHeight, focalLine, interludeAnchor,
			interludeHeight, 0);
		scrollOffset = clamp(scrollOffset, layout.minOffset(),
			layout.maxOffset());
		layout.commit(containerHeight, scrollOffset, interludeAnchor,
			interludeHeight, 0);

		if(layout.hasInterlude() && interludeActive)
		{
			long end = timeline.interludeEnd();
			boolean reset = reason.resetInterlude() || lastInterludeEnd != end;
			interludeDots.setInterlude(timeline.interludeStart(), end,
				timeline.getCurrentTime(), reset);
			lastInterludeEnd = end;
		}else
		{
			interludeDots.clear();
			lastInterludeEnd = Long.MIN_VALUE;
		}

		int passedBoundary = interludeActive
			? timeline.interludeAnchorLine() + 1
			: scrollTo;
		boolean narrow = isNarrowViewport();
		boolean snapTweens = reason == AmlLayoutReason.REBUILD_VIEW
			|| reason == AmlLayoutReason.CONFIG_CHANGE;
		double delay = 0;
		double baseDelay = 0.05;

		for(int i = 0; i < lineCount; i++)
		{
			LineRender r = renders[i];
			double curPos = layout.lineY(i);
			boolean inViewport = layout.isInViewport(i);
			boolean highlighted = timeline.isHighlighted(i);
			boolean active = AmlVisual.isActive(highlighted, i, scrollTo,
				latest);

			r.y = curPos;
			r.isInViewport = inViewport;
			r.isActive = active;
			r.text = texts[i];
			r.translation = translations[i];
			r.romanization = romanizations[i];
			r.background = backgrounds[i];
			r.hasRuby = hasRuby[i];
			r.opacity = AmlVisual.opacity(inViewport, highlighted, nonDynamic,
				hidePassedLines, playing, i, passedBoundary);
			r.blurLevel = AmlVisual.blurLevel(i, active, inViewport,
				touchScrolled, enableBlur, scrollTo, latest, narrow);

			double targetMainScale = enableScale
				? AmlVisual.mainScale(active, playing) : 100;
			double targetBgScale = enableScale
				? AmlVisual.backgroundScale(active, playing) : 100;
			double slideTarget = AmlVisual.backgroundSlideTarget(active,
				playing, false);

			boolean snap = reason.snapPosY();
			if(snap)
			{
				posYSprings[i].setPosition(curPos);
				bgSlideSprings[i].setPosition(slideTarget);
			}else
			{
				posYSprings[i].setTargetPosition(curPos, delay);
				bgSlideSprings[i].setTargetPosition(slideTarget, delay);
			}
			mainScaleSprings[i].setTargetPosition(targetMainScale, delay);
			bgScaleSprings[i].setTargetPosition(targetBgScale, delay);

			// CSS 过渡：.lyricLineWrapper 的 opacity / filter 0.4s ease
			setTween(opacityTweens[i], r.opacity,
				AmlVisual.OPACITY_TRANSITION_SECONDS, snapTweens);
			setTween(blurTweens[i], r.blurLevel,
				AmlVisual.BLUR_TRANSITION_SECONDS, snapTweens);
			// CSS 过渡：遮罩 alpha，激活 0.3s、退回 0.45s，均 ease-out
			double[] masks = AmlVisual.maskAlphaTargets(active);
			double maskSeconds = AmlVisual.maskTransitionSeconds(active);
			setTween(brightMaskTweens[i], masks[0], maskSeconds, snapTweens);
			setTween(darkMaskTweens[i], masks[1], maskSeconds, snapTweens);

			if(reason.disableStagger())
				continue;
			if(curPos + layout.lineHeight(i) >= 0)
			{
				delay += baseDelay;
				if(i >= scrollTo)
					baseDelay /= 1.05;
			}
		}
	}

	private static void setTween(AmlTween tween, double target, double seconds,
		boolean snap)
	{
		if(snap)
			tween.snapTo(target);
		else
			tween.setTarget(target, seconds);
	}

	private double interludeHeight()
	{
		return interludeDotSize()
			+ AmlVisual.INTERLUDE_DOT_MARGIN_EM * fontSize * 2;
	}

	/** dot 尺寸对应 CSS {@code clamp(0.5em, 1vh, 3em)}。 */
	private double interludeDotSize()
	{
		int screenH = Minecraft.getInstance() == null ? 240
			: Minecraft.getInstance().getWindow().getGuiScaledHeight();
		double oneVh = screenH / 100.0;
		return Math.max(0.5 * fontSize, Math.min(oneVh, 3 * fontSize));
	}

	private static boolean isNarrowViewport()
	{
		Minecraft mc = Minecraft.getInstance();
		return mc != null && mc.getWindow()
			.getGuiScaledWidth() <= AmlVisual.NARROW_VIEWPORT_WIDTH;
	}

	public void update()
	{
		measureHeights();
		long now = System.nanoTime();
		double delta = Math.min(0.1,
			(now - lastUpdateNanos) / 1_000_000_000D);
		lastUpdateNanos = now;

		for(int i = 0; i < lineCount; i++)
		{
			posYSprings[i].update(delta);
			bgSlideSprings[i].update(delta);
			mainScaleSprings[i].update(delta);
			bgScaleSprings[i].update(delta);
			opacityTweens[i].update(delta);
			blurTweens[i].update(delta);
			brightMaskTweens[i].update(delta);
			darkMaskTweens[i].update(delta);
		}
		interludeDots.setPlaying(playing);
		interludeDots.update(timeline.getCurrentTime());

		if(autoAlignSuspended
			&& now - lastInteractionNanos > AUTO_ALIGN_RESUME_MS * 1_000_000L)
		{
			autoAlignSuspended = false;
			touchScrolled = false;
			scrollOffset = 0;
			calcLayout(AmlLayoutReason.INTERACTION_END);
		}
	}

	// ---------- 渲染 ----------

	/**
	 * 用 Minecraft 字体路径绘制歌词。
	 *
	 * <p>AMLL 的歌词只有白色一种前景色，层次由遮罩 alpha 与模糊表达，
	 * 因此这里不接受颜色参数。</p>
	 */
	public void render(GuiGraphics graphics, int areaLeft, int areaTop,
		int areaRight, int areaBottom)
	{
		setContentWidth(areaRight - areaLeft);
		setContainerHeight(areaBottom - areaTop);
		measureHeights();
		Font font = Minecraft.getInstance().font;
		graphics.enableScissor(areaLeft, areaTop, areaRight, areaBottom);
		try
		{
			for(int i = 0; i < lineCount; i++)
			{
				LineRender r = renders[i];
				if(!r.isInViewport)
					continue;
				double screenY = areaTop + posYSprings[i].getCurrentPosition();
				if(screenY + layout.lineHeight(i) < areaTop - 8
					|| screenY > areaBottom + 8)
					continue;
				renderLineMc(graphics, font, i, areaLeft, screenY);
			}
			drawInterludeMc(graphics, areaLeft, areaTop);
		}finally
		{
			graphics.disableScissor();
		}
	}

	private void renderLineMc(GuiGraphics graphics, Font font, int index,
		int areaLeft, double screenY)
	{
		LineRender r = renders[index];
		double opacity = opacityTweens[index].get()
			* (r.background ? AmlVisual.BG_LINE_OPACITY : 1);
		if(opacity <= 0.002)
			return;
		double size = lineFontSize(index) * scale(index);
		double blurPx = AmlVisual.blurPx(blurTweens[index].get());
		double bright = brightMaskTweens[index].get();
		double dark = darkMaskTweens[index].get();
		// 逐行歌词没有词元素、不套遮罩，暗部完全由 opacity 承担
		double uniformMask = nonDynamic ? 1 : bright;
		boolean gradient = !nonDynamic && Math.abs(bright - dark) > 1e-3;
		Style weight = styleFor(index, r.isActive);
		double rubyShift = r.hasRuby ? size * AmlVisual.SUB_LINE_SCALE : 0;
		double used = drawLineMc(graphics, font, index, areaLeft,
			screenY + rubyShift, size, weight, opacity, uniformMask, bright,
			dark, gradient, blurPx, rubyShift);

		double subX = areaLeft + AmlVisual.LINE_PADDING_EM * fontSize;
		double subY = screenY + used;
		double subAlpha = opacity * AmlVisual.SUB_LINE_OPACITY;
		// AMLL 的子行顺序：翻译行在前、音译行在后
		if(!r.translation.isBlank())
		{
			drawTextAt(graphics, font, r.translation, PingFangFont.LIGHT_STYLE,
				subX, subY, subFontSize() / MC_GLYPH,
				alphaColor(subAlpha), null, 0);
			subY += subFontSize() * 1.5;
		}
		if(!r.romanization.isBlank())
			drawTextAt(graphics, font, r.romanization, PingFangFont.LIGHT_STYLE,
				subX, subY, subFontSize() / MC_GLYPH, alphaColor(subAlpha), null,
				0);
	}

	private double drawLineMc(GuiGraphics graphics, Font font, int index,
		int areaLeft, double originY, double size, Style weight, double opacity,
		double uniformMask, double bright, double dark, boolean gradient,
		double blurPx, double rubyShift)
	{
		double padding = AmlVisual.LINE_PADDING_EM * fontSize;
		double maxWidth = contentWidth - padding * 2;
		double scale = size / MC_GLYPH;
		LyricWord[] lineWords = words[index];
		AmlEmphasize.Word[] anims = wordAnims[index];
		int[] breaks = lineBreaks[index];
		long lineRelative = timeline.getCurrentTime() - starts[index];
		double rowHeight = size * ROW_LEADING;
		double x = 0;
		double y = 0;
		int breakCursor = 0;

		if(lineWords.length == 0)
		{
			drawTextAt(graphics, font, texts[index], weight, areaLeft + padding,
				originY, scale, alphaColor(opacity * uniformMask), null, blurPx);
			return rubyShift + rowHeight;
		}

		for(int w = 0; w < lineWords.length; w++)
		{
			LyricWord word = lineWords[w];
			AmlEmphasize.Word anim = anims[w];
			double wordWidth = PingFangFont.width(font, word.text(), weight)
				* scale;
			if(w > 0 && breakCursor < breaks.length && breaks[breakCursor] == w)
			{
				x = 0;
				y += rowHeight;
				breakCursor++;
			}else if(breaks.length == 0 && x > 0 && x + wordWidth > maxWidth)
			{
				x = 0;
				y += rowHeight;
			}
			double wordX = areaLeft + padding + x;
			double wordY = originY + y;

			if(word.hasRuby())
				drawRuby(graphics, font, word, weight, wordX, wordY, size,
					scale, opacity);

			if(gradient && anim != null && anim.emphasize())
				drawEmphasizedWord(graphics, font, word, anim, weight, wordX,
					wordY, scale, size, opacity, bright, dark, lineRelative);
			else if(gradient)
				drawKaraokeWord(graphics, font, word.text(), weight, wordX,
					wordY, scale, size, opacity,
					word.progressAt(timeline.getCurrentTime()), bright, dark);
			else
				drawTextAt(graphics, font, word.text(), weight, wordX, wordY,
					scale, alphaColor(opacity * uniformMask), null, blurPx);

			x += wordWidth;
		}
		return rubyShift + y + rowHeight;
	}

	/** 注音：{@code font-size: 0.5em; line-height: 1em}，居中于词上方。 */
	private void drawRuby(GuiGraphics graphics, Font font, LyricWord word,
		Style weight, double wordX, double wordY, double size, double scale,
		double opacity)
	{
		StringBuilder joined = new StringBuilder();
		for(LyricRuby segment : word.ruby())
			joined.append(segment.text());
		if(joined.length() == 0)
			return;
		double rubySize = size * AmlVisual.SUB_LINE_SCALE;
		double rubyScale = rubySize / MC_GLYPH;
		double wordWidth = PingFangFont.width(font, word.text(), weight) * scale;
		double rubyWidth = PingFangFont.width(font, joined.toString(),
			PingFangFont.LIGHT_STYLE) * rubyScale;
		drawTextAt(graphics, font, joined.toString(), PingFangFont.LIGHT_STYLE,
			wordX + Math.max(0, (wordWidth - rubyWidth) / 2),
			wordY - rubySize, rubyScale, alphaColor(opacity * 0.5), null, 0);
	}

	/** 非强调词：整词绘制，用精确的渐变带遮罩。 */
	private void drawKaraokeWord(GuiGraphics graphics, Font font, String text,
		Style weight, double x, double y, double scale, double size,
		double opacity, double progress, double bright, double dark)
	{
		double width = PingFangFont.width(font, text, weight) * scale;
		double fadeWidth = size * ROW_LEADING * AmlVisual.WORD_FADE_WIDTH;
		drawMasked(graphics, font, text, weight, x, y, scale, size, opacity,
			progress, width, fadeWidth, bright, dark);
	}

	/** 强调词：逐字应用缩放、位移与辉光。 */
	private void drawEmphasizedWord(GuiGraphics graphics, Font font,
		LyricWord word, AmlEmphasize.Word anim, Style weight, double x, double y,
		double scale, double size, double opacity, double bright, double dark,
		long lineRelative)
	{
		String text = word.text();
		int count = anim.charCount();
		double cursor = x;
		double progress = word.progressAt(timeline.getCurrentTime());

		int index = 0;
		for(int offset = 0; offset < text.length();)
		{
			int codePoint = text.codePointAt(offset);
			String glyph = new String(Character.toChars(codePoint));
			offset += Character.charCount(codePoint);

			double glyphWidth = PingFangFont.width(font, glyph, weight) * scale;
			AmlEmphasize.Frame frame = AmlEmphasize.evaluate(anim, index,
				lineRelative);

			// AMLL 的 emphasize 是 CSS transform：绕字心缩放并整体平移，遮罩仍按
			// 变换前的元素尺寸定义，因此先算缩放后的盒子再把原点挪到盒子左上角
			double glyphScale = frame.scale();
			double scaledWidth = glyphWidth * glyphScale;
			double scaledSize = size * glyphScale;
			double drawX = cursor + glyphWidth / 2 + frame.offsetXEm() * size
				- scaledWidth / 2;
			double drawY = y + size / 2 + frame.offsetYEm() * size
				- scaledSize / 2;
			double fadeWidth = scaledSize * ROW_LEADING
				* AmlVisual.WORD_FADE_WIDTH;

			if(frame.glowAlpha() > 0.01)
				drawGlow(graphics, font, glyph, weight, drawX, drawY,
					scale * glyphScale, frame, opacity, scaledSize);

			double glyphProgress = glyphProgressAt(progress, index, count);
			drawMasked(graphics, font, glyph, weight, drawX, drawY,
				scale * glyphScale, scaledSize, opacity, glyphProgress,
				scaledWidth, fadeWidth, bright, dark);

			cursor += glyphWidth;
			index++;
		}
	}

	/**
	 * 精确的渐变遮罩：按 AMLL 的遮罩图几何算出亮部边界与暗部边界，再把
	 * 「交界前亮部 / 渐变带 / 交界后暗部」画成**互不重叠**的三段裁切带，
	 * 每个像素只绘制一次，因此等价于 AMLL 的 CSS 遮罩。
	 *
	 * <p>注意渐变**不居中于交界处**——它整段位于亮部边界右侧（见
	 * {@link AmlVisual#maskEdges}）。早先「先整词压暗再叠画亮部」的居中写法既会
	 * 让暗部被合成两次而偏亮，几何上也与 AMLL 有最多半个渐变宽的偏差。</p>
	 *
	 * <p>这里固定不施加模糊：AMLL 中激活行的 {@code resolveBlurLevel} 恒为 0，
	 * 模糊只出现在非激活行；而非激活行走的是单色均匀遮罩路径，不会进到这里。
	 * 状态切换的过渡期间若对每条裁切带各自模糊，带与带之间会因模糊溢出被裁掉
	 * 而出现接缝，因此宁可短暂不加模糊。</p>
	 */
	private void drawMasked(GuiGraphics graphics, Font font, String text,
		Style weight, double x, double y, double scale, double size,
		double opacity, double progress, double width, double fadeWidth,
		double bright, double dark)
	{
		int top = (int)Math.floor(y);
		int bottom = (int)Math.ceil(y + size * ROW_LEADING);
		double[] edges = AmlVisual.maskEdges(width, fadeWidth, progress);
		double brightEdge = edges[AmlVisual.MASK_BRIGHT_EDGE];
		double rampStart = clamp(brightEdge, 0, width);
		double rampEnd = clamp(edges[AmlVisual.MASK_DARK_EDGE], rampStart, width);

		// 亮部边界左侧（含整词已唱完的情形）
		if(rampStart > 0)
			drawTextAt(graphics, font, text, weight, x, y, scale,
				alphaColor(opacity * bright),
				new int[]{(int)Math.round(x), top,
					(int)Math.round(x + rampStart), bottom}, 0);
		// 渐变带：按 1px 一档切片，使得阶梯足够细
		if(rampEnd > rampStart)
		{
			int steps = (int)clamp(Math.ceil(rampEnd - rampStart), 2, 24);
			double step = (rampEnd - rampStart) / steps;
			for(int s = 0; s < steps; s++)
			{
				double sliceStart = rampStart + step * s;
				double sliceEnd = s == steps - 1 ? rampEnd : sliceStart + step;
				double mid = (sliceStart + sliceEnd) / 2;
				double sliceAlpha = AmlVisual.maskAlphaAt(mid, brightEdge,
					fadeWidth, bright, dark);
				drawTextAt(graphics, font, text, weight, x, y, scale,
					alphaColor(opacity * sliceAlpha),
					new int[]{(int)Math.round(x + sliceStart), top,
						(int)Math.round(x + sliceEnd), bottom}, 0);
			}
		}
		// 暗部边界右侧（含整词未唱的情形）
		if(rampEnd < width)
			drawTextAt(graphics, font, text, weight, x, y, scale,
				alphaColor(opacity * dark),
				new int[]{(int)Math.round(x + rampEnd), top,
					(int)Math.round(x + width), bottom}, 0);
	}

	/** 强调辉光：对应 {@code text-shadow: 0 0 blur em rgba(255,255,255,glow)}。 */
	private void drawGlow(GuiGraphics graphics, Font font, String glyph,
		Style weight, double x, double y, double scale,
		AmlEmphasize.Frame frame, double opacity, double size)
	{
		double glowAlpha = opacity * frame.glowAlpha() * 0.55;
		if(glowAlpha <= 0.02)
			return;
		double radius = frame.glowBlurEm() * size;
		int steps = 4;
		for(int i = 1; i <= steps; i++)
		{
			double angle = Math.PI * 2 * i / steps;
			double ox = Math.cos(angle) * radius * 0.5;
			double oy = Math.sin(angle) * radius * 0.5;
			drawTextAt(graphics, font, glyph, weight, x + ox, y + oy, scale,
				alphaColor(glowAlpha / steps), null, 0);
		}
	}

	private double scale(int index)
	{
		Spring spring = backgrounds[index] ? bgScaleSprings[index]
			: mainScaleSprings[index];
		return spring.getCurrentPosition() / 100D;
	}

	private static int alphaColor(double opacity)
	{
		return whiteWithAlpha(opacity);
	}

	/**
	 * 以绝对屏幕坐标绘制缩放文字，可选裁切矩形 {@code [left, top, right, bottom]}
	 * 与高斯模糊半径。
	 *
	 * <p>Minecraft 的 {@code Font.drawInBatch} 会把 alpha 小于 4 的颜色强制改成
	 * 不透明，所以这里对近乎透明的颜色直接跳过，否则本该隐形的行会整行变实。</p>
	 */
	private void drawTextAt(GuiGraphics graphics, Font font, String text,
		Style weight, double x, double y, double scale, int color,
		int[] scissor, double blurPx)
	{
		if(text == null || text.isEmpty() || color >>> 24 < 4)
			return;
		if(scissor != null
			&& (scissor[2] <= scissor[0] || scissor[3] <= scissor[1]))
			return;

		double sigma = mcBlurEnabled ? blurPx : 0;
		if(sigma < MIN_BLUR_PX)
		{
			drawTextOnce(graphics, font, text, weight, x, y, scale, color,
				scissor);
			return;
		}
		// 高斯采样近似：模糊半径不超过 5px，可见行数很少，开销可控
		for(double[] tap : blurTaps(sigma))
		{
			int tapAlpha = (int)Math.round((color >>> 24) * tap[2]);
			if(tapAlpha < 4)
				continue;
			drawTextOnce(graphics, font, text, weight, x + tap[0], y + tap[1],
				scale, tapAlpha << 24 | color & 0x00FFFFFF, scissor);
		}
	}

	private void drawTextOnce(GuiGraphics graphics, Font font, String text,
		Style weight, double x, double y, double scale, int color,
		int[] scissor)
	{
		if(scissor != null)
			graphics.enableScissor(scissor[0], scissor[1], scissor[2],
				scissor[3]);
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale((float)scale, (float)scale, 1);
		graphics.drawString(font, PingFangFont.text(text, weight), 0, 0, color,
			false);
		graphics.pose().popPose();
		if(scissor != null)
			graphics.disableScissor();
	}

	/** 二维高斯采样表，返回 {@code [dx, dy, weight]}，权重已归一化。 */
	private static double[][] blurTaps(double sigma)
	{
		int key = (int)Math.round(sigma * 4);
		double[][] cached = BLUR_TAP_CACHE.get(key);
		if(cached != null)
			return cached;

		int radius = Math.max(1, (int)Math.ceil(sigma * 2));
		List<double[]> taps = new ArrayList<>();
		double twoSigmaSquared = 2 * sigma * sigma;
		for(int dy = -radius; dy <= radius; dy++)
			for(int dx = -radius; dx <= radius; dx++)
			{
				double weight = Math.exp(-(dx * dx + dy * dy) / twoSigmaSquared);
				if(weight < MIN_BLUR_TAP_WEIGHT)
					continue;
				taps.add(new double[]{dx, dy, weight});
			}
		taps.sort((a, b) -> Double.compare(b[2], a[2]));
		if(taps.size() > MAX_BLUR_TAPS)
			taps = new ArrayList<>(taps.subList(0, MAX_BLUR_TAPS));
		double kept = 0;
		for(double[] tap : taps)
			kept += tap[2];
		double[][] result = new double[taps.size()][];
		for(int i = 0; i < taps.size(); i++)
		{
			double[] tap = taps.get(i);
			result[i] = new double[]{tap[0], tap[1], tap[2] / kept};
		}
		BLUR_TAP_CACHE.put(key, result);
		return result;
	}

	// ---------- Skia 渲染 ----------

	public void renderSkia(org.jetbrains.skia.Canvas canvas, int areaLeft,
		int areaTop, int areaRight, int areaBottom)
	{
		setContentWidth(areaRight - areaLeft);
		setContainerHeight(areaBottom - areaTop);
		measureHeights();
		net.wurstclient.render.skia.SkiaFontManager fonts =
			net.wurstclient.render.skia.SkiaFontManager.get();
		for(int i = 0; i < lineCount; i++)
		{
			LineRender r = renders[i];
			if(!r.isInViewport)
				continue;
			double screenY = areaTop + posYSprings[i].getCurrentPosition();
			if(screenY + layout.lineHeight(i) < areaTop - 8
				|| screenY > areaBottom + 8)
				continue;
			renderLineSkia(canvas, fonts, i, areaLeft, screenY);
		}
		drawInterludeSkia(canvas, areaLeft, areaTop);
	}

	private void renderLineSkia(org.jetbrains.skia.Canvas canvas,
		net.wurstclient.render.skia.SkiaFontManager fonts, int index,
		int areaLeft, double screenY)
	{
		LineRender r = renders[index];
		double opacity = opacityTweens[index].get()
			* (r.background ? AmlVisual.BG_LINE_OPACITY : 1);
		if(opacity <= 0.002)
			return;
		double size = lineFontSize(index) * scale(index);
		double blurPx = AmlVisual.blurPx(blurTweens[index].get());
		org.jetbrains.skia.Typeface typeface = r.background ? fonts.light()
			: r.isActive ? fonts.semibold()
				: blurPx >= 3 ? fonts.light() : fonts.regular();
		double bright = brightMaskTweens[index].get();
		double dark = darkMaskTweens[index].get();
		double uniformMask = nonDynamic ? 1 : bright;
		boolean gradient = !nonDynamic && Math.abs(bright - dark) > 1e-3;
		double rubyShift = r.hasRuby ? size * AmlVisual.SUB_LINE_SCALE : 0;

		double used = drawLineSkia(canvas, typeface, index, areaLeft,
			screenY + rubyShift, size, opacity, uniformMask, bright, dark,
			gradient, blurPx);

		double subX = areaLeft + AmlVisual.LINE_PADDING_EM * fontSize;
		double subY = screenY + used;
		double subAlpha = opacity * AmlVisual.SUB_LINE_OPACITY;
		if(!r.translation.isBlank())
		{
			drawSkiaSubLine(canvas, fonts, r.translation, subX, subY, subAlpha);
			subY += subFontSize() * 1.5;
		}
		if(!r.romanization.isBlank())
			drawSkiaSubLine(canvas, fonts, r.romanization, subX, subY, subAlpha);
	}

	private void drawSkiaSubLine(org.jetbrains.skia.Canvas canvas,
		net.wurstclient.render.skia.SkiaFontManager fonts, String text, double x,
		double y, double alpha)
	{
		if(alpha <= 0.004)
			return;
		org.jetbrains.skia.Font font = new org.jetbrains.skia.Font(fonts.light(),
			(float)subFontSize());
		org.jetbrains.skia.TextLine line =
			org.jetbrains.skia.TextLine.Companion.make(text, font);
		org.jetbrains.skia.Paint paint = new org.jetbrains.skia.Paint();
		paint.setColor(0xFFFFFFFF);
		paint.setAlphaf((float)clamp(alpha, 0, 1));
		canvas.drawTextLine(line, (float)x,
			(float)(y - font.getMetrics().getAscent()), paint);
	}

	private double drawLineSkia(org.jetbrains.skia.Canvas canvas,
		org.jetbrains.skia.Typeface typeface, int index, int areaLeft,
		double originY, double size, double opacity, double uniformMask,
		double bright, double dark, boolean gradient, double blurPx)
	{
		double padding = AmlVisual.LINE_PADDING_EM * fontSize;
		double maxWidth = contentWidth - padding * 2;
		org.jetbrains.skia.Font font = new org.jetbrains.skia.Font(typeface,
			(float)size);
		double baseline = originY - font.getMetrics().getAscent();
		LyricWord[] lineWords = words[index];
		AmlEmphasize.Word[] anims = wordAnims[index];
		int[] breaks = lineBreaks[index];
		long lineRelative = timeline.getCurrentTime() - starts[index];
		double rowHeight = size * ROW_LEADING;
		double x = 0;
		double y = 0;
		int breakCursor = 0;

		org.jetbrains.skia.Paint basePaint = new org.jetbrains.skia.Paint();
		basePaint.setColor(0xFFFFFFFF);
		basePaint.setAlphaf((float)clamp(opacity * uniformMask, 0, 1));
		if(blurPx > 0.01)
			basePaint.setImageFilter(org.jetbrains.skia.ImageFilter.Companion
				.makeBlur((float)blurPx, (float)blurPx,
					org.jetbrains.skia.FilterTileMode.CLAMP, null, null));

		if(lineWords.length == 0)
		{
			drawSkiaText(canvas, texts[index], font,
				(float)(areaLeft + padding), (float)baseline, basePaint);
			return rowHeight;
		}

		for(int w = 0; w < lineWords.length; w++)
		{
			LyricWord word = lineWords[w];
			AmlEmphasize.Word anim = anims[w];
			org.jetbrains.skia.TextLine textLine =
				org.jetbrains.skia.TextLine.Companion.make(word.text(), font);
			double wordWidth = textLine.getWidth();
			if(w > 0 && breakCursor < breaks.length && breaks[breakCursor] == w)
			{
				x = 0;
				y += rowHeight;
				breakCursor++;
			}else if(breaks.length == 0 && x > 0 && x + wordWidth > maxWidth)
			{
				x = 0;
				y += rowHeight;
			}
			double wordX = areaLeft + padding + x;
			double wordY = baseline + y;
			double floatY = anim != null && gradient
				? -anim.upEm() * fontSize
					* AmlEasing.easeOut(progressOf(lineRelative, anim))
				: 0;

			if(!gradient)
				drawSkiaText(canvas, textLine, (float)wordX,
					(float)(wordY + floatY), basePaint);
			else if(anim != null && anim.emphasize())
				drawEmphasizedWordSkia(canvas, font, word, anim, wordX,
					wordY + floatY, opacity, blurPx, lineRelative, bright, dark);
			else
				drawSkiaKaraoke(canvas, textLine, wordX, wordY + floatY,
					opacity, word.progressAt(timeline.getCurrentTime()),
					fontSize * ROW_LEADING * AmlVisual.WORD_FADE_WIDTH, blurPx,
					bright, dark);

			x += wordWidth;
		}
		return y + rowHeight;
	}

	private void drawSkiaText(org.jetbrains.skia.Canvas canvas, String text,
		org.jetbrains.skia.Font font, float x, float y,
		org.jetbrains.skia.Paint paint)
	{
		org.jetbrains.skia.TextLine line =
			org.jetbrains.skia.TextLine.Companion.make(text, font);
		drawSkiaText(canvas, line, x, y, paint);
	}

	private void drawSkiaText(org.jetbrains.skia.Canvas canvas,
		org.jetbrains.skia.TextLine line, float x, float y,
		org.jetbrains.skia.Paint paint)
	{
		canvas.drawTextLine(line, x, y, paint);
	}

	/**
	 * Skia 卡拉 OK：单次绘制，用线性渐变的 alpha 复刻 AMLL 的 CSS 遮罩。
	 *
	 * <p>亮部与暗部是同一字形的两种遮罩透明度，停靠点位置由
	 * {@link AmlVisual#maskEdges} 给出（渐变整段位于亮部边界右侧，而非居中于
	 * 交界处）。分两次绘制会让暗部叠加两次而偏亮，因此必须一次画完。</p>
	 */
	private void drawSkiaKaraoke(org.jetbrains.skia.Canvas canvas,
		org.jetbrains.skia.TextLine line, double x, double y, double opacity,
		double progress, double fadeWidth, double blurPx, double bright,
		double dark)
	{
		double width = Math.max(0.001, line.getWidth());
		org.jetbrains.skia.Paint paint = new org.jetbrains.skia.Paint();
		if(blurPx > 0.01)
			paint.setImageFilter(org.jetbrains.skia.ImageFilter.Companion
				.makeBlur((float)blurPx, (float)blurPx,
					org.jetbrains.skia.FilterTileMode.CLAMP, null, null));

		int brightColor = whiteWithAlpha(opacity * bright);
		int darkColor = whiteWithAlpha(opacity * dark);
		double[] edges = AmlVisual.maskEdges(width, fadeWidth, progress);
		double brightEdge = edges[AmlVisual.MASK_BRIGHT_EDGE];
		double rampStart = clamp(brightEdge, 0, width);
		double rampEnd = clamp(edges[AmlVisual.MASK_DARK_EDGE], rampStart, width);

		// 渐变不跨越整个词时退化为单色绘制，避免退化 shader
		if(rampEnd <= 0)
		{
			paint.setColor(darkColor);
			canvas.drawTextLine(line, (float)x, (float)y, paint);
			return;
		}
		if(rampStart >= width)
		{
			paint.setColor(brightColor);
			canvas.drawTextLine(line, (float)x, (float)y, paint);
			return;
		}

		float p1 = (float)(rampStart / width);
		float p2 = (float)(rampEnd / width);
		int[] colors = {brightColor, brightColor, darkColor, darkColor};
		float[] positions = {0F, p1, p2, 1F};
		paint.setShader(org.jetbrains.skia.Shader.Companion.makeLinearGradient(
			new org.jetbrains.skia.Point((float)x, (float)y),
			new org.jetbrains.skia.Point((float)(x + width), (float)y), colors,
			positions));
		canvas.drawTextLine(line, (float)x, (float)y, paint);
	}

	/** Skia 逐字强调：每个字符绕自身中心缩放，叠加横向让位、纵向抬升与辉光。 */
	private void drawEmphasizedWordSkia(org.jetbrains.skia.Canvas canvas,
		org.jetbrains.skia.Font font, LyricWord word, AmlEmphasize.Word anim,
		double x, double y, double opacity, double blurPx, long lineRelative,
		double bright, double dark)
	{
		String text = word.text();
		int count = anim.charCount();
		double fadeWidth = fontSize * ROW_LEADING * AmlVisual.WORD_FADE_WIDTH;
		double progress = word.progressAt(timeline.getCurrentTime());
		double cursor = x;
		int index = 0;

		for(int offset = 0; offset < text.length();)
		{
			int codePoint = text.codePointAt(offset);
			String glyph = new String(Character.toChars(codePoint));
			offset += Character.charCount(codePoint);

			org.jetbrains.skia.TextLine line =
				org.jetbrains.skia.TextLine.Companion.make(glyph, font);
			double glyphWidth = line.getWidth();
			AmlEmphasize.Frame frame = AmlEmphasize.evaluate(anim, index,
				lineRelative);
			double glyphScale = frame.scale();

			double centerX = cursor + glyphWidth / 2
				+ frame.offsetXEm() * fontSize;
			double centerY = y - fontSize * 0.35
				+ frame.offsetYEm() * fontSize;

			canvas.save();
			canvas.translate((float)centerX, (float)centerY);
			canvas.scale((float)glyphScale, (float)glyphScale);
			canvas.translate((float)-(cursor + glyphWidth / 2),
				(float)-(y - fontSize * 0.35));

			if(frame.glowAlpha() > 0.01)
				drawSkiaGlow(canvas, line, cursor, y,
					opacity * frame.glowAlpha(),
					blurPx + frame.glowBlurEm() * fontSize);

			double glyphProgress = glyphProgressAt(progress, index, count);
			drawSkiaKaraoke(canvas, line, cursor, y, opacity, glyphProgress,
				fadeWidth, blurPx, bright, dark);
			canvas.restore();

			cursor += glyphWidth;
			index++;
		}
	}

	/** 强调辉光：同一字形加高斯模糊后以低透明度铺在底层。 */
	private void drawSkiaGlow(org.jetbrains.skia.Canvas canvas,
		org.jetbrains.skia.TextLine line, double x, double y,
		double glowOpacity, double glowBlurPx)
	{
		double alpha = clamp(glowOpacity, 0, 1) * 0.55;
		if(alpha <= 0.01)
			return;
		org.jetbrains.skia.Paint glow = new org.jetbrains.skia.Paint();
		glow.setColor(whiteWithAlpha(alpha));
		float radius = (float)Math.max(0.5, glowBlurPx);
		glow.setImageFilter(org.jetbrains.skia.ImageFilter.Companion.makeBlur(
			radius, radius, org.jetbrains.skia.FilterTileMode.CLAMP, null,
			null));
		canvas.drawTextLine(line, (float)x, (float)y, glow);
	}

	// ---------- 间奏点 ----------

	private void drawInterludeMc(GuiGraphics graphics, int areaLeft,
		int areaTop)
	{
		if(!interludeDots.isVisible())
			return;
		double em = fontSize;
		double dotSize = interludeDotSize() * interludeDots.scale();
		double radius = dotSize / 2;
		double gap = 0.25 * em + 4;
		double left = areaLeft + AmlVisual.INTERLUDE_DOT_PADDING_EM * em;
		double centerY = areaTop + layout.interludeY()
			+ AmlVisual.INTERLUDE_DOT_MARGIN_EM * em + radius;
		for(int d = 0; d < 3; d++)
		{
			int alpha = (int)Math.round(
				255 * AmlVisual.clamp01(interludeDots.dotOpacity(d)));
			if(alpha <= 0)
				continue;
			int cx = (int)Math.round(left + d * (dotSize + gap) + radius);
			int cy = (int)Math.round(centerY);
			int r = Math.max(1, (int)Math.round(radius));
			graphics.fill(cx - r, cy - r, cx + r, cy + r,
				alpha << 24 | 0xFFFFFF);
		}
	}

	private void drawInterludeSkia(org.jetbrains.skia.Canvas canvas,
		int areaLeft, int areaTop)
	{
		if(!interludeDots.isVisible())
			return;
		double em = fontSize;
		double dotSize = interludeDotSize() * interludeDots.scale();
		double radius = dotSize / 2;
		double gap = 0.25 * em + 4;
		double left = areaLeft + AmlVisual.INTERLUDE_DOT_PADDING_EM * em;
		double centerY = areaTop + layout.interludeY()
			+ AmlVisual.INTERLUDE_DOT_MARGIN_EM * em + radius;
		for(int d = 0; d < 3; d++)
		{
			org.jetbrains.skia.Paint dot = new org.jetbrains.skia.Paint();
			dot.setColor(0xFFFFFFFF);
			dot.setAlphaf(
				(float)AmlVisual.clamp01(interludeDots.dotOpacity(d)));
			canvas.drawCircle((float)(left + d * (dotSize + gap) + radius),
				(float)centerY, (float)radius, dot);
		}
	}

	// ---------- 工具 ----------

	/** AMLL {@code LyricLineBase.shouldEmphasize}，保留给测试与外部调用。 */
	public static boolean shouldEmphasize(LyricWord word)
	{
		return AmlEmphasize.shouldEmphasize(word);
	}

	private static double progressOf(long lineRelativeMs,
		AmlEmphasize.Word anim)
	{
		if(anim.floatDurationMs() <= 0)
			return 1;
		double t = (lineRelativeMs - anim.floatDelayMs())
			/ anim.floatDurationMs();
		return clamp(t, 0, 1);
	}

	private static double glyphProgressAt(double wordProgress, int index,
		int count)
	{
		if(count <= 1)
			return wordProgress;
		double start = index / (double)count;
		double end = (index + 1) / (double)count;
		if(wordProgress <= start)
			return 0;
		if(wordProgress >= end)
			return 1;
		return (wordProgress - start) / (end - start);
	}

	private static int whiteWithAlpha(double opacity)
	{
		return (int)Math.round(255 * clamp(opacity, 0, 1)) << 24 | 0x00FFFFFF;
	}

	private static double clamp(double value, double min, double max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
