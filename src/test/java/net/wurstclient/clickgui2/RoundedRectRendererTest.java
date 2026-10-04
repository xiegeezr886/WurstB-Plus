/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Tests the pure parts of the rounded rectangle renderer.
 *
 * <p>
 * The geometry needs a GL context and cannot be tested here, but the vertex
 * colour interpolation can: it is plain arithmetic, and getting it wrong shows
 * up as a colour that jumps in the middle of the progress bar.
 */
final class RoundedRectRendererTest
{
	private static final float EPSILON = 0.0001F;

	/** 参考实现进度条的两端：`#2563eb` 与 `#0d9488`。 */
	private static final int ACCENT = 0xFF2563EB;
	private static final int TEAL = 0xFF0D9488;

	@Test
	void theAxisRatioIsTheClampedPositionInTheRange()
	{
		assertEquals(0F, RoundedRectRenderer.axisRatio(0, 0, 10), EPSILON);
		assertEquals(0.5F, RoundedRectRenderer.axisRatio(5, 0, 10), EPSILON);
		assertEquals(1F, RoundedRectRenderer.axisRatio(10, 0, 10), EPSILON);
		assertEquals(0.25F, RoundedRectRenderer.axisRatio(2.5F, 0, 10), EPSILON);
	}

	@Test
	void theAxisRatioClampsOutsideTheRange()
	{
		assertEquals(0F, RoundedRectRenderer.axisRatio(-5, 0, 10), EPSILON);
		assertEquals(1F, RoundedRectRenderer.axisRatio(15, 0, 10), EPSILON);
	}

	@Test
	void aDegenerateOrReversedRangeFallsBackToZero()
	{
		assertEquals(0F, RoundedRectRenderer.axisRatio(3, 0, 0), EPSILON);
		assertEquals(0F, RoundedRectRenderer.axisRatio(3, 5, 5), EPSILON);
		// 区间反了：原来的 lerpByY 也是返回 0，行为保持不变
		assertEquals(0F, RoundedRectRenderer.axisRatio(3, 10, 0), EPSILON);
	}

	@Test
	void theColourInterpolationHitsBothEnds()
	{
		assertEquals(ACCENT, RoundedRectRenderer.lerpColor(ACCENT, TEAL, 0F));
		assertEquals(TEAL, RoundedRectRenderer.lerpColor(ACCENT, TEAL, 1F));
	}

	@Test
	void theColourInterpolationClampsOutsideTheRange()
	{
		assertEquals(ACCENT, RoundedRectRenderer.lerpColor(ACCENT, TEAL, -1F));
		assertEquals(TEAL, RoundedRectRenderer.lerpColor(ACCENT, TEAL, 2F));
	}

	/**
	 * 逐通道取整，不是整数除法：{@code #2563eb} 与 {@code #0d9488} 的中点是
	 * {@code #197cba}（红 25、绿 124、蓝 186）。
	 */
	@Test
	void theColourInterpolationIsPerChannelAndRounded()
	{
		assertEquals(0xFF197CBA,
			RoundedRectRenderer.lerpColor(ACCENT, TEAL, 0.5F));
		assertEquals(0xFF1F6FD2,
			RoundedRectRenderer.lerpColor(ACCENT, TEAL, 0.25F));
	}

	@Test
	void theAlphaChannelIsInterpolatedToo()
	{
		assertEquals(0x40FFFFFF,
			RoundedRectRenderer.lerpColor(0x80FFFFFF, 0x00FFFFFF, 0.5F));
		assertEquals(0x00FFFFFF,
			RoundedRectRenderer.lerpColor(0x80FFFFFF, 0x00FFFFFF, 1F));
	}
}
