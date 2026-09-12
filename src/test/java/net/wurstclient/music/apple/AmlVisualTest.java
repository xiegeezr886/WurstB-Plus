package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 校验 {@link AmlVisual} 复刻的 AMLL 视觉公式。
 */
final class AmlVisualTest
{
	@Test
	void highlightedLinesUsePointEightFive()
	{
		assertEquals(0.85,
			AmlVisual.opacity(true, true, false, false, true, 0, 0), 1e-9);
	}

	@Test
	void nonDynamicLyricsDimToPointTwo()
	{
		assertEquals(0.2,
			AmlVisual.opacity(true, false, true, false, true, 5, 0), 1e-9);
	}

	@Test
	void dynamicLyricsStayFullyOpaque()
	{
		assertEquals(1,
			AmlVisual.opacity(true, false, false, false, true, 5, 0), 1e-9);
	}

	@Test
	void outOfViewportAndHiddenPassedLines()
	{
		assertEquals(0,
			AmlVisual.opacity(false, false, false, false, true, 0, 0), 1e-9);
		assertEquals(AmlVisual.HIDDEN_PASSED_OPACITY, AmlVisual
			.opacity(true, false, false, true, true, 1, 3), 1e-12);
		// 暂停时 hidePassedLines 不生效
		assertEquals(1,
			AmlVisual.opacity(true, false, false, true, false, 1, 3), 1e-9);
	}

	@Test
	void activeFollowsHighlightedOrScrollWindow()
	{
		assertTrue(AmlVisual.isActive(true, 9, 0, 0));
		assertTrue(AmlVisual.isActive(false, 3, 3, 5));
		assertFalse(AmlVisual.isActive(false, 2, 3, 5));
		assertFalse(AmlVisual.isActive(false, 5, 3, 5));
	}

	@Test
	void blurLevelCountsDistanceFromFocus()
	{
		// 焦点之后的距离按 latestHighlightedIndex 计
		assertEquals(1, AmlVisual.blurLevel(4, false, true, false, true, 3, 4,
			false), 1e-9);
		// 焦点之前额外 +1，避免与高亮行同档：|3-1|+1 = 3 → 档位 4
		assertEquals(4, AmlVisual.blurLevel(1, false, true, false, true, 3, 4,
			false), 1e-9);
	}

	@Test
	void blurDisabledAndOutOfViewportShortCircuits()
	{
		assertEquals(0, AmlVisual.blurLevel(9, false, true, false, false, 0, 0,
			false), 1e-9);
		assertEquals(AmlVisual.OUT_OF_VIEW_BLUR, AmlVisual.blurLevel(9, false,
			false, false, true, 0, 0, false), 1e-9);
		// 触摸滚动或焦点行不模糊
		assertEquals(0, AmlVisual.blurLevel(9, true, true, false, true, 0, 0,
			false), 1e-9);
		assertEquals(0, AmlVisual.blurLevel(9, false, true, true, true, 0, 0,
			false), 1e-9);
	}

	@Test
	void narrowViewportDiscountsBlur()
	{
		double wide = AmlVisual.blurLevel(1, false, true, false, true, 3, 4,
			false);
		double narrow = AmlVisual.blurLevel(1, false, true, false, true, 3, 4,
			true);
		assertEquals(wide * AmlVisual.NARROW_VIEWPORT_BLUR_SCALE, narrow, 1e-9);
	}

	@Test
	void blurIsCappedAtFivePixels()
	{
		assertEquals(5, AmlVisual.blurPx(12), 1e-9);
		assertEquals(2, AmlVisual.blurPx(2), 1e-9);
	}

	@Test
	void scalesFollowActiveAndPlaying()
	{
		assertEquals(100, AmlVisual.mainScale(true, true), 1e-9);
		assertEquals(97, AmlVisual.mainScale(false, true), 1e-9);
		// 暂停时不缩放
		assertEquals(100, AmlVisual.mainScale(false, false), 1e-9);
		assertEquals(100, AmlVisual.backgroundScale(true, true), 1e-9);
		assertEquals(75, AmlVisual.backgroundScale(false, true), 1e-9);
	}

	@Test
	void backgroundSlideHidesWhenInactiveAndPlaying()
	{
		assertEquals(0, AmlVisual.backgroundSlideTarget(true, true, false),
			1e-9);
		assertEquals(-80, AmlVisual.backgroundSlideTarget(false, true, false),
			1e-9);
		assertEquals(80, AmlVisual.backgroundSlideTarget(false, true, true),
			1e-9);
		assertEquals(0, AmlVisual.backgroundSlideTarget(false, false, false),
			1e-9);
	}

	@Test
	void backgroundActiveScaleRamp()
	{
		assertEquals(1.0, AmlVisual.backgroundActiveScale(0), 1e-9);
		assertEquals(0.8, AmlVisual.backgroundActiveScale(-80), 1e-9);
		assertEquals(0.9, AmlVisual.backgroundActiveScale(-40), 1e-9);
	}

	@Test
	void maskEdgesMatchAmllMaskPositionSweep()
	{
		double c = 100;
		double f = 10;
		// p = 0：maskPos = -(C + f)，亮部边界落在元素左侧之外、暗部边界正好在 0
		double[] start = AmlVisual.maskEdges(c, f, 0);
		assertEquals(-10, start[AmlVisual.MASK_BRIGHT_EDGE], 1e-9);
		assertEquals(0, start[AmlVisual.MASK_DARK_EDGE], 1e-9);
		// p = 1：maskPos = 0，两个边界都在元素右侧之外
		double[] end = AmlVisual.maskEdges(c, f, 1);
		assertEquals(100, end[AmlVisual.MASK_BRIGHT_EDGE], 1e-9);
		assertEquals(110, end[AmlVisual.MASK_DARK_EDGE], 1e-9);
		// p = 0.5：与「居中于交界处」的近似写法恰好重合
		double[] mid = AmlVisual.maskEdges(c, f, 0.5);
		assertEquals(45, mid[AmlVisual.MASK_BRIGHT_EDGE], 1e-9);
		assertEquals(55, mid[AmlVisual.MASK_DARK_EDGE], 1e-9);
	}

	@Test
	void maskEdgesAdvanceMonotonicallyAndClamp()
	{
		double previous = Double.NEGATIVE_INFINITY;
		for(int i = 0; i <= 20; i++)
		{
			double[] edges = AmlVisual.maskEdges(80, 12, i / 20D);
			assertTrue(edges[AmlVisual.MASK_BRIGHT_EDGE] > previous,
				"亮部边界应随进度单调右移");
			assertEquals(edges[AmlVisual.MASK_BRIGHT_EDGE] + 12,
				edges[AmlVisual.MASK_DARK_EDGE], 1e-9);
			previous = edges[AmlVisual.MASK_BRIGHT_EDGE];
		}
		// 越界进度被夹住
		assertEquals(AmlVisual.maskEdges(80, 12, 0)[0],
			AmlVisual.maskEdges(80, 12, -5)[0], 1e-9);
		assertEquals(AmlVisual.maskEdges(80, 12, 1)[0],
			AmlVisual.maskEdges(80, 12, 7)[0], 1e-9);
	}

	@Test
	void wholeWordIsDarkBeforeStartAndBrightAfterEnd()
	{
		double c = 100;
		double f = 10;
		double[] start = AmlVisual.maskEdges(c, f, 0);
		double[] end = AmlVisual.maskEdges(c, f, 1);
		for(double x = 0; x <= c; x += 10)
		{
			assertEquals(AmlVisual.ACTIVE_DARK_MASK_ALPHA,
				AmlVisual.maskAlphaAt(x, start[AmlVisual.MASK_BRIGHT_EDGE], f,
					AmlVisual.ACTIVE_BRIGHT_MASK_ALPHA,
					AmlVisual.ACTIVE_DARK_MASK_ALPHA),
				1e-9, "未开始时 x=" + x + " 应完全处于暗部");
			assertEquals(AmlVisual.ACTIVE_BRIGHT_MASK_ALPHA,
				AmlVisual.maskAlphaAt(x, end[AmlVisual.MASK_BRIGHT_EDGE], f,
					AmlVisual.ACTIVE_BRIGHT_MASK_ALPHA,
					AmlVisual.ACTIVE_DARK_MASK_ALPHA),
				1e-9, "唱完后 x=" + x + " 应完全处于亮部");
		}
	}

	@Test
	void brightEdgeSweepsExactlyWordPlusFadeWidth()
	{
		double c = 100;
		double f = 10;
		double atStart = AmlVisual.maskEdges(c, f, 0)[AmlVisual.MASK_BRIGHT_EDGE];
		double atEnd = AmlVisual.maskEdges(c, f, 1)[AmlVisual.MASK_BRIGHT_EDGE];
		// 遮罩平移范围是 -(C + f) → 0，因此亮部边界正好扫过 C + f
		assertEquals(-f, atStart, 1e-9);
		assertEquals(c, atEnd, 1e-9);
		assertEquals(c + f, atEnd - atStart, 1e-9);
	}

	@Test
	void rampIsInsideTheWordForEveryIntermediateProgress()
	{
		double c = 100;
		double f = 10;
		// 只有 p = 0 / p = 1 时整词才分别是全暗 / 全亮；中间任意时刻元素内
		// 必定同时存在渐变带的两侧
		for(int i = 1; i < 20; i++)
		{
			double[] edges = AmlVisual.maskEdges(c, f, i / 20D);
			assertTrue(edges[AmlVisual.MASK_BRIGHT_EDGE] < c,
				"亮部边界应已进入词内，p=" + i / 20D);
			assertTrue(edges[AmlVisual.MASK_DARK_EDGE] > 0,
				"暗部边界应仍在词内或右侧，p=" + i / 20D);
		}
	}

	@Test
	void maskAlphaInterpolatesLinearlyInsideTheRamp()
	{
		double bright = 1.0;
		double dark = 0.4;
		assertEquals(bright, AmlVisual.maskAlphaAt(50, 50, 20, bright, dark),
			1e-9);
		assertEquals(0.7, AmlVisual.maskAlphaAt(60, 50, 20, bright, dark), 1e-9);
		assertEquals(dark, AmlVisual.maskAlphaAt(70, 50, 20, bright, dark), 1e-9);
		// 渐变带之外保持两端值
		assertEquals(bright, AmlVisual.maskAlphaAt(0, 50, 20, bright, dark),
			1e-9);
		assertEquals(dark, AmlVisual.maskAlphaAt(999, 50, 20, bright, dark),
			1e-9);
	}
}
