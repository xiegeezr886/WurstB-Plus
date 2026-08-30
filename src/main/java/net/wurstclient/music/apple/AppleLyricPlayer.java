package net.wurstclient.music.apple;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.wurstclient.clickgui2.PingFangFont;
import net.wurstclient.music.LyricLine;

/**
 * applemusic-like-lyrics {@code LyricPlayerBase} 的 1:1 Java 移植
 * （去掉 DOM，渲染层输出纯几何/视觉指令）。
 *
 * <p>主循环：{@link #setLyricLines} 重建视图 → {@link #setCurrentTime} 同步
 * 时间线 → {@link #calcLayout} 计算布局（焦点/滚动边界/行指令）→ 每帧
 * {@link #update} 推进每行弹簧 → {@link #render} 按指令绘制。行视觉：当前
 * 高亮行 GRADIENT 模式（已唱亮/未唱灰），非高亮行缩放 97% 并以模糊等级
 * 折算透明度；阶梯延迟 0.05s 起逐行衰减；间奏点 3 点呼吸动画；滚动停止
 * 5 秒后恢复自动对齐。</p>
 */
public final class AppleLyricPlayer
{
	/** 行视觉状态：渲染层的输出指令。 */
	public static final class LineRender
	{
		public double y;
		public boolean isInViewport;
		public boolean isActive;
		public float opacity;
		public float scale;
		public int blurLevel;
		public float progress;
		public String text;
	}

	// 布局配置（layout.ts LayoutConfig 默认值）
	private static final float ALIGN_POSITION = 0.35F;

	// 弹簧参数（index.ts 默认值）
	private static final double POS_Y_MASS = 0.9;
	private static final double POS_Y_DAMPING = 15;
	private static final double POS_Y_STIFFNESS = 90;
	private static final double SCALE_MASS = 2;
	private static final double SCALE_DAMPING = 25;
	private static final double SCALE_STIFFNESS = 100;

	// 缓慢模式（spring.ts SLOW）
	private static final double SLOW_STIFFNESS = 90;
	private static final double SLOW_DAMPING = 15;

	// 正常播放动态弹簧（spring.ts getPosYSpringPolicy）
	private static final double MIN_INTERVAL = 100;
	private static final double MAX_INTERVAL = 800;
	private static final double MIN_STIFFNESS = 170;
	private static final double MAX_STIFFNESS = 220;
	private static final double DAMPING_MULTIPLIER = 2.2;
	private static final double INTERVAL_EXPONENT = 0.2;

	/** 滚动停止后自动恢复自动对齐的时长（毫秒）。 */
	private static final long AUTO_ALIGN_RESUME_MS = 5_000;

	/** 默认回退行高（Apple Music: containerHeight / 5 的估算，这里取 24px）。 */
	private static final double DEFAULT_LINE_HEIGHT = 24;

	private final AppleTimeline timeline = new AppleTimeline();
	private final AppleLayout layout = new AppleLayout();
	private Spring[] posYSprings = new Spring[0];
	private Spring[] scaleSprings = new Spring[0];
	private LineRender[] renders = new LineRender[0];
	private double[] staggerDelays = new double[0];

	private long[] starts = new long[0];
	private long[] ends = new long[0];
	private String[] texts = new String[0];
	private String[][] lineWords = new String[0][];
	private float[][][] lineWordFractions = new float[0][][];
	private int lineCount;
	private double containerHeight;
	private double scrollOffset;
	private boolean autoAlignSuspended;
	private long lastInteractionNanos;
	private boolean isPlaying = true;
	private long lastUpdateNanos = System.nanoTime();

	private boolean hasInterlude;
	private double interludeY;
	private long interludeStartMs;
	private long interludeEndMs;
	private final Spring interludeSpring = new Spring(0);

	public void setLyricLines(List<LyricLine> lines, long initialTime)
	{
		lineCount = lines.size();
		starts = new long[lineCount];
		ends = new long[lineCount];
		texts = new String[lineCount];
		lineWords = new String[lineCount][];
		lineWordFractions = new float[lineCount][][];
		for(int i = 0; i < lineCount; i++)
		{
			LyricLine line = lines.get(i);
			starts[i] = line.timeMs();
			ends[i] = i + 1 < lineCount ? lines.get(i + 1).timeMs()
				: line.timeMs() + 8_000;
			texts[i] = line.text();
			lineWords[i] = LyricWordSplitter.split(texts[i]).toArray(
				new String[0]);
			lineWordFractions[i] = LyricWordSplitter.wordFractions(
				java.util.Arrays.asList(lineWords[i]));
		}
		posYSprings = new Spring[lineCount];
		scaleSprings = new Spring[lineCount];
		renders = new LineRender[lineCount];
		staggerDelays = new double[lineCount];
		for(int i = 0; i < lineCount; i++)
		{
			posYSprings[i] = new Spring(0);
			posYSprings[i].updateParams(POS_Y_STIFFNESS, POS_Y_DAMPING,
				POS_Y_MASS, false);
			scaleSprings[i] = new Spring(100);
			scaleSprings[i].updateParams(SCALE_STIFFNESS, SCALE_DAMPING,
				SCALE_MASS, false);
			renders[i] = new LineRender();
		}
		rebuildView(initialTime);
	}

	private void rebuildView(long initialTime)
	{
		timeline.setTimeBounds(starts, ends);
		layout.initHeights(lineCount, DEFAULT_LINE_HEIGHT);
		scrollOffset = 0;
		autoAlignSuspended = false;
		setCurrentTime(initialTime, true);
	}

	public void setContainerHeight(double height)
	{
		containerHeight = height;
	}

	public void setPlaying(boolean playing)
	{
		if(isPlaying == playing)
			return;
		isPlaying = playing;
		calcLayout();
	}

	public void setCurrentTime(long time, boolean isSeek)
	{
		timeline.sync(time, isSeek);
		if(!timeline.hasChanged())
			return;

		if(timeline.isTimeJumped() && !autoAlignSuspended)
			resetScroll();

		if(timeline.isInterludeChanged() || timeline.isScrollToChanged()
			|| timeline.isTimeJumped())
			updateSpringParams(timeline.activeInterlude() != -1);

		calcLayout();
	}

	public long getCurrentTime()
	{
		return timeline.getCurrentTime();
	}

	/** 手动滚动（滚轮）。delta 为滚轮增量。 */
	public void scroll(double delta)
	{
		scrollOffset = clamp(scrollOffset - delta * 24,
			layout.minOffset(), layout.maxOffset());
		autoAlignSuspended = true;
		lastInteractionNanos = System.nanoTime();
		calcLayout();
	}

	public void resetScroll()
	{
		scrollOffset = 0;
		autoAlignSuspended = false;
	}

	/** 将鼠标 Y 映射回最近的歌词行（点击跳转用）。 */
	public int hitLine(double mouseY, double top, double bottom)
	{
		if(lineCount == 0 || mouseY < top || mouseY > bottom)
			return -1;
		int best = -1;
		double bestDist = Double.MAX_VALUE;
		for(int i = 0; i < lineCount; i++)
		{
			double dist = Math.abs(renders[i].y - mouseY);
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
		return starts[index];
	}

	private void updateSpringParams(boolean interludeActive)
	{
		int scrollTo = timeline.scrollToIndex();
		double stiffness;
		double damping;
		if(timeline.isSeeking() || interludeActive || scrollTo <= 0)
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
			stiffness = MIN_STIFFNESS
				+ ratio * (MAX_STIFFNESS - MIN_STIFFNESS);
			damping = Math.sqrt(stiffness) * DAMPING_MULTIPLIER;
		}
		for(Spring spring : posYSprings)
			spring.updateParams(stiffness, damping, POS_Y_MASS, false);
	}

	private void calcLayout()
	{
		int focalLine = timeline.scrollToIndex();
		int interludeAnchor = -1;
		double interludeHeight = 0;
		if(timeline.activeInterlude() != -1)
		{
			interludeAnchor = timeline.interludeAnchorLine();
			interludeHeight = 18;
		}
		layout.beginFrame(containerHeight, focalLine, interludeAnchor,
			interludeHeight, 0);
		scrollOffset = clamp(scrollOffset, layout.minOffset(),
			layout.maxOffset());
		layout.commit(containerHeight, scrollOffset, interludeAnchor,
			interludeHeight, 0);

		hasInterlude = layout.hasInterlude();
		interludeY = layout.interludeY();
		if(hasInterlude)
			interludeSpring.setTargetPosition(interludeY, 0);
		if(timeline.activeInterlude() != -1)
		{
			interludeStartMs = timeline.interludeStart();
			interludeEndMs = timeline.interludeEnd();
		}

		// 视觉指令（对应 index.ts 的 setTransform 循环）
		int latestIndex = timeline.latestHighlightedIndex();
		double delay = 0;
		double baseDelay = 0.05;
		for(int i = 0; i < lineCount; i++)
		{
			LineRender r = renders[i];
			double curPos = layout.lineY(i);
			r.y = curPos;
			r.isInViewport = layout.isInViewport(i);
			r.text = texts[i];
			boolean hasHighlighted = timeline.isHighlighted(i);
			boolean isActive = hasHighlighted
				|| i >= timeline.scrollToIndex() && i < latestIndex;

			int blurLevel = 0;
			float opacity = 1;
			if(!r.isInViewport)
			{
				blurLevel = 5;
				opacity = 0;
			}else
			{
				if(!autoAlignSuspended && !isActive)
				{
					blurLevel = 1;
					if(i < timeline.scrollToIndex())
						blurLevel += Math.abs(timeline.scrollToIndex() - i)
							+ 1;
					else
						blurLevel += Math.abs(i - latestIndex);
				}
				if(hasHighlighted)
					opacity = 0.85F;
			}

			float scale = 100;
			if(!isActive && isPlaying)
				scale = 97;

			r.isActive = isActive;
			r.opacity = opacity;
			r.scale = scale / 100F;
			r.blurLevel = blurLevel;
			r.progress = isActive ? lineProgress(i) : 0;

			if(curPos + DEFAULT_LINE_HEIGHT >= 0 && !timeline.isSeeking())
			{
				staggerDelays[i] = delay;
				delay += baseDelay;
				if(i >= timeline.scrollToIndex())
					baseDelay /= 1.05;
			}else
				staggerDelays[i] = 0;

			// 弹簧目标：posY 与 scale（对应 group.setTransform）
			posYSprings[i].setTargetPosition(curPos, staggerDelays[i]);
			scaleSprings[i].setTargetPosition(scale, staggerDelays[i]);
		}
	}

	private float lineProgress(int index)
	{
		if(index < 0 || index >= lineCount)
			return 0;
		long start = starts[index];
		long end = ends[index];
		if(end <= start)
			return 0;
		long pos = timeline.getCurrentTime();
		return (float)Math.max(0, Math.min(1,
			(pos - start) / (double)(end - start)));
	}

	/** 每帧推进（弹簧更新 + 自动对齐恢复计时）。 */
	public void update()
	{
		long now = System.nanoTime();
		double delta = Math.min(0.1,
			(now - lastUpdateNanos) / 1_000_000_000D);
		lastUpdateNanos = now;

		for(int i = 0; i < lineCount; i++)
		{
			posYSprings[i].update(delta);
			scaleSprings[i].update(delta);
		}
		interludeSpring.update(delta);

		if(autoAlignSuspended
			&& now - lastInteractionNanos > AUTO_ALIGN_RESUME_MS * 1_000_000L)
		{
			autoAlignSuspended = false;
			scrollOffset = 0;
		}
	}

	/**
	 * 渲染一帧。area 为歌词区域（屏幕坐标）。
	 */
	public void render(GuiGraphics graphics, int areaLeft, int areaTop,
		int areaRight, int areaBottom, int sungColor, int unsungColor)
	{
		Font font = Minecraft.getInstance().font;
		int centerX = (areaLeft + areaRight) / 2;
		for(int i = 0; i < lineCount; i++)
		{
			LineRender r = renders[i];
			if(!r.isInViewport || r.opacity <= 0.001F)
				continue;
			double y = posYSprings[i].getCurrentPosition();
			if(y + DEFAULT_LINE_HEIGHT < areaTop - 40
				|| y > areaBottom + 40)
				continue;

			float blurFade = switch(r.blurLevel)
			{
				case 0 -> 1;
				case 1 -> 0.88F;
				case 2 -> 0.72F;
				case 3 -> 0.56F;
				default -> 0.4F;
			};
			int alpha = Math.round(255 * r.opacity * blurFade);
			if(alpha <= 0)
				continue;

			String shown = PingFangFont.trim(font, r.text,
				areaRight - areaLeft - 16);
			int textY = (int)Math.round(y + (DEFAULT_LINE_HEIGHT - 9) / 2D);

			if(r.isActive)
			{
				// 当前高亮行：逐词渲染（Semibold 字重）——
				// 已唱词亮色、未唱词灰、正在唱的词两段渐变
				renderActiveLine(graphics, font, i, r, shown, centerX,
					textY, areaLeft, areaRight, areaTop, areaBottom,
					alpha, sungColor, unsungColor);
			}else
			{
				// 非高亮行：按与滚动焦点的距离选字重
				int distance = Math.abs(i - timeline.scrollToIndex());
				net.minecraft.network.chat.Style weight =
					distance <= 1 ? PingFangFont.REGULAR_STYLE
						: PingFangFont.LIGHT_STYLE;
				int textW = PingFangFont.width(font, shown, weight);
				int textX = centerX - textW / 2;
				graphics.drawString(font,
					PingFangFont.text(shown, weight), textX, textY,
					alpha << 24 | 0xFFFFFF, false);
			}
		}

		// 间奏点（3 点循环呼吸动画：相位差 1/3 周期，对应原版 CSS 动画）
		if(hasInterlude)
		{
			long nowMs = System.currentTimeMillis();
			double interludePos = interludeSpring.getCurrentPosition();
			for(int d = 0; d < 3; d++)
			{
				double phase = (nowMs / 600D + d / 3D) % 1D;
				double breathe = 0.5 + 0.5 * Math.sin(phase * Math.PI * 2);
				int dotAlpha = 70 + (int)Math.round(170 * breathe);
				int dotX = centerX - 14 + d * 12;
				int dotY = (int)Math.round(interludePos + 6);
				graphics.fill(dotX, dotY, dotX + 4, dotY + 4,
					dotAlpha << 24 | 0xFFFFFF);
			}
		}
	}

	/**
	 * Skia 矢量渲染路径（PVPUtils region 管线）：画布已由
	 * {@link net.wurstclient.render.skia.SkiaRegionRenderer} 预置
	 * scale(guiScale) + translate(-region)，本方法直接以 GUI 坐标绘制。
	 * 真缩放（97%）、真模糊（ImageFilter）、真词级渐变（LinearGradient）、
	 * 辉光强调（时长 ≥1s 的词）。sung/unsung 为 ARGB 色值，alpha 通道
	 * 作为行不透明度的乘数（MC 字体路径的 drawString 无法携带该 alpha，
	 * 本路径予以保留）。
	 */
	public void renderSkia(org.jetbrains.skia.Canvas canvas, int areaLeft,
		int areaTop, int areaRight, int areaBottom, int sungColor,
		int unsungColor)
	{
		net.wurstclient.render.skia.SkiaFontManager fonts =
			net.wurstclient.render.skia.SkiaFontManager.get();
		float sungAlpha = ((sungColor >>> 24) & 0xFF) / 255F;
		float unsungAlpha = ((unsungColor >>> 24) & 0xFF) / 255F;
		int sungRgb = sungColor & 0x00FFFFFF;
		int unsungRgb = unsungColor & 0x00FFFFFF;
		int centerX = (areaLeft + areaRight) / 2;

		for(int i = 0; i < lineCount; i++)
		{
			LineRender r = renders[i];
			if(!r.isInViewport || r.opacity <= 0.001F)
				continue;
			double y = posYSprings[i].getCurrentPosition();
			if(y + DEFAULT_LINE_HEIGHT < areaTop - 40
				|| y > areaBottom + 40)
				continue;

			float blurFade = switch(r.blurLevel)
			{
				case 0 -> 1;
				case 1 -> 0.88F;
				case 2 -> 0.72F;
				case 3 -> 0.56F;
				default -> 0.4F;
			};
			float opacity = r.opacity * blurFade;
			if(opacity <= 0.01F)
				continue;

			// 字重 + 真缩放（Apple Music 97%）
			org.jetbrains.skia.Typeface typeface = r.isActive
				? fonts.semibold()
				: Math.abs(i - timeline.scrollToIndex()) <= 1
					? fonts.regular() : fonts.light();
			float fontSize = 9 * r.scale;
			org.jetbrains.skia.Font font =
				new org.jetbrains.skia.Font(typeface, fontSize);
			float baseline = (float)Math.round(
				y + (DEFAULT_LINE_HEIGHT - 9) / 2D)
				+ font.getMetrics().getAscent();

			String shown = r.text;
			org.jetbrains.skia.TextLine line =
				org.jetbrains.skia.TextLine.Companion.make(shown, font);
			float textX = centerX - line.getWidth() / 2F;

			org.jetbrains.skia.Paint paint =
				new org.jetbrains.skia.Paint();
			paint.setColor(0xFFFFFFFF);
			paint.setAlphaf(opacity);

			// 真模糊（blurLevel → sigma，GUI 单位）
			if(r.blurLevel > 0 && !r.isActive)
			{
				float sigma = r.blurLevel * 0.8F;
				paint.setImageFilter(org.jetbrains.skia.ImageFilter.Companion
					.makeBlur(sigma, sigma,
						org.jetbrains.skia.FilterTileMode.CLAMP, null, null));
			}

			if(!r.isActive)
			{
				canvas.drawTextLine(line, textX, baseline, paint);
				continue;
			}

			// 当前高亮行：词级 LinearGradient（已唱亮 → 未唱灰）
			String[] words = lineWords[i];
			float[][] fractions = lineWordFractions[i];
			float cursorX = textX;
			for(int w = 0; w < words.length; w++)
			{
				String word = words[w];
				if(word.isEmpty())
					continue;
				org.jetbrains.skia.TextLine wordLine =
					org.jetbrains.skia.TextLine.Companion.make(word, font);
				float wordW = wordLine.getWidth();
				float startF = fractions[w][0];
				float endF = fractions[w][1];

				org.jetbrains.skia.Paint wordPaint =
					new org.jetbrains.skia.Paint();
				wordPaint.setAlphaf(opacity);

				if(r.progress >= endF)
				{
					wordPaint.setColor(sungRgb);
					wordPaint.setAlphaf(opacity * sungAlpha);
					canvas.drawTextLine(wordLine, cursorX, baseline,
						wordPaint);
				}else if(r.progress <= startF)
				{
					wordPaint.setColor(unsungRgb);
					wordPaint.setAlphaf(opacity * unsungAlpha);
					canvas.drawTextLine(wordLine, cursorX, baseline,
						wordPaint);
				}else
				{
					// 正在唱的词：水平渐变 shader（wordFadeWidth 0.5em）
					float wordProgress = (r.progress - startF)
						/ (endF - startF);
					float fadeHalf = 0.25F * fontSize;
					float boundary = cursorX + wordW * wordProgress;
					float pos1 = (float)clamp((boundary - fadeHalf - cursorX)
						/ wordW, 0, 1);
					float pos2 = (float)clamp((boundary + fadeHalf - cursorX)
						/ wordW, pos1, 1);
					int sungArgb = sungRgb | (int)(255 * sungAlpha) << 24;
					int unsungArgb = unsungRgb
						| (int)(255 * unsungAlpha) << 24;
					int[] colors = {sungArgb, sungArgb, unsungArgb,
						unsungArgb};
					float[] positions = {0, pos1, pos2, 1};
					org.jetbrains.skia.Shader shader =
						org.jetbrains.skia.Shader.Companion
							.makeLinearGradient(
								new org.jetbrains.skia.Point(cursorX, baseline),
								new org.jetbrains.skia.Point(cursorX + wordW, baseline),
								colors, positions);
					wordPaint.setShader(shader);
					canvas.drawTextLine(wordLine, cursorX, baseline,
						wordPaint);

					// 辉光强调：词时长 ≥1s 时叠加模糊光晕
					double wordDurationMs = (endF - startF)
						* (ends[i] - starts[i]);
					if(wordDurationMs >= 1000)
					{
						org.jetbrains.skia.Paint glow =
							new org.jetbrains.skia.Paint();
						glow.setColor(sungRgb);
						glow.setAlphaf(opacity * 0.35F * sungAlpha);
						glow.setImageFilter(
							org.jetbrains.skia.ImageFilter.Companion.makeBlur(
								2.5F, 2.5F,
								org.jetbrains.skia.FilterTileMode.CLAMP,
								null, null));
						canvas.drawTextLine(wordLine, cursorX, baseline, glow);
					}
				}
				cursorX += wordW;
			}
		}

		// 间奏点（循环呼吸）
		if(hasInterlude)
		{
			long nowMs = System.currentTimeMillis();
			double interludePos = interludeSpring.getCurrentPosition();
			for(int d = 0; d < 3; d++)
			{
				double phase = (nowMs / 600D + d / 3D) % 1D;
				double breathe = 0.5 + 0.5 * Math.sin(phase * Math.PI * 2);
				float alpha = (float)(0.3 + 0.65 * breathe);
				org.jetbrains.skia.Paint dot =
					new org.jetbrains.skia.Paint();
				dot.setColor(0xFFFFFFFF);
				dot.setAlphaf(alpha);
				float dotX = centerX - 14 + d * 12;
				float dotY = (float)(interludePos + 6);
				float dotR = 2F;
				canvas.drawCircle(dotX, dotY, dotR, dot);
			}
		}
	}

	private void renderActiveLine(GuiGraphics graphics, Font font,
		int lineIndex, LineRender r, String shown, int centerX, int textY,
		int areaLeft, int areaRight, int areaTop, int areaBottom, int alpha,
		int sungColor, int unsungColor)
	{
		// 按 Semibold 字重测量整行宽度并逐词绘制
		String[] words = lineWords[lineIndex];
		float[][] fractions = lineWordFractions[lineIndex];
		int totalWidth = PingFangFont.width(font, shown,
			PingFangFont.SEMIBOLD_STYLE);
		int cursorX = centerX - totalWidth / 2;

		for(int w = 0; w < words.length; w++)
		{
			String word = words[w];
			if(word.isEmpty())
				continue;
			float startF = fractions[w][0];
			float endF = fractions[w][1];
			int wordW = PingFangFont.width(font, word,
				PingFangFont.SEMIBOLD_STYLE);
			net.minecraft.network.chat.Component wordComponent =
				PingFangFont.text(word, PingFangFont.SEMIBOLD_STYLE);

			if(r.progress >= endF)
			{
				// 已唱完：亮色
				graphics.drawString(font, wordComponent, cursorX, textY,
					alpha << 24 | sungColor & 0x00FFFFFF, false);
			}else if(r.progress <= startF)
			{
				// 未唱：灰
				graphics.drawString(font, wordComponent, cursorX, textY,
					alpha << 24 | unsungColor & 0x00FFFFFF, false);
			}else
			{
				// 正在唱：词内两段（先灰整词，再 scissor 亮已唱部分），
				// 分界处叠加 2px 半透明过渡带对应 wordFadeWidth 渐变
				graphics.drawString(font, wordComponent, cursorX, textY,
					alpha << 24 | unsungColor & 0x00FFFFFF, false);
				float wordProgress = (r.progress - startF) / (endF - startF);
				int sungEnd = Math.round(cursorX + wordW * wordProgress);
				int scissorLeft = Math.max(areaLeft + 4, cursorX);
				int scissorRight = Math.min(areaRight - 4, sungEnd);
				if(scissorRight > scissorLeft)
				{
					graphics.enableScissor(scissorLeft, areaTop,
						scissorRight - scissorLeft, areaBottom - areaTop);
					graphics.drawString(font, wordComponent, cursorX, textY,
						alpha << 24 | sungColor & 0x00FFFFFF, false);
					graphics.disableScissor();
				}
				// 2px 过渡带：已唱色与未唱色各半透明混合
				int blendAlpha = alpha >> 1;
				graphics.fill(sungEnd - 1, textY, sungEnd,
					textY + 9, blendAlpha << 24 | sungColor & 0x00FFFFFF);
				graphics.fill(sungEnd, textY, sungEnd + 1,
					textY + 9, blendAlpha << 24 | unsungColor & 0x00FFFFFF);
			}
			cursorX += wordW;
		}
	}

	private static double clamp(double value, double min, double max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
