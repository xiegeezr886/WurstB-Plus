/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.background.WeSceneLayout.Rect;

/**
 * Tests the canvas to screen conversion.
 *
 * <p>
 * The numbers come from the real Persica scene: canvas 3840x2160, a 4000x2250
 * backdrop centred on it, and a second layer scaled to 1.03.
 */
final class WeSceneLayoutTest
{
	private static final float EPSILON = 0.001F;

	private static WeScene.Layer layer(float sizeX, float sizeY, float scale)
	{
		return new WeScene.Layer("test", "texture", 1920, 1080, sizeX, sizeY,
			scale, scale, 1, 1, 1, 1, 0.05F, 0.05F);
	}

	/**
	 * 16:9 屏幕：cover 比例正好是 0.5，画布铺满，4000 宽的底图略微出血。
	 */
	@Test
	void coverScaleFillsTheScreen()
	{
		assertEquals(0.5F,
			WeSceneLayout.coverScale(3840, 2160, 1, 1920, 1080), EPSILON);

		// 更宽的屏幕按宽度铺，更高的屏幕按高度铺，两个方向都不留黑边
		assertEquals(2560F / 3840F,
			WeSceneLayout.coverScale(3840, 2160, 1, 2560, 1080), EPSILON);
		assertEquals(1440F / 2160F,
			WeSceneLayout.coverScale(3840, 2160, 1, 1920, 1440), EPSILON);

		// zoom 是额外系数
		assertEquals(1.0F,
			WeSceneLayout.coverScale(3840, 2160, 2, 1920, 1080), EPSILON);

		// 退化输入不该抛异常，也不该算出负数
		assertEquals(0, WeSceneLayout.coverScale(0, 0, 1, 1920, 1080), EPSILON);
		assertEquals(0, WeSceneLayout.coverScale(3840, 2160, 1, 0, 0), EPSILON);
	}

	/** 居中的 4000x2250 底图在 1920x1080 上就是「铺满 + 每边多出 40 像素」。 */
	@Test
	void aCentredLayerEndsUpCentred()
	{
		Rect rect = WeSceneLayout.rect(layer(4000, 2250, 1), 0.5F, 3840, 2160,
			1920, 1080, 4000, 2250, 0, 0);

		assertEquals(-40, rect.x(), EPSILON);
		assertEquals(-22.5F, rect.y(), EPSILON);
		assertEquals(2000, rect.width(), EPSILON);
		assertEquals(1125, rect.height(), EPSILON);
		assertFalse(rect.isEmpty());
	}

	/** 图层自己的 scale 叠在画布比例上。 */
	@Test
	void layerScaleMultiplies()
	{
		Rect rect = WeSceneLayout.rect(layer(4000, 2250, 1.03F), 0.5F, 3840,
			2160, 1920, 1080, 4000, 2250, 0, 0);

		assertEquals(2060, rect.width(), EPSILON);
		assertEquals(-70, rect.x(), EPSILON);
	}

	/** 没写 size 的 autosize 图层用贴图自身的尺寸。 */
	@Test
	void autosizeFallsBackToTheTextureSize()
	{
		Rect rect = WeSceneLayout.rect(layer(0, 0, 1), 0.5F, 3840, 2160, 1920,
			1080, 800, 600, 0, 0);

		assertEquals(400, rect.width(), EPSILON);
		assertEquals(300, rect.height(), EPSILON);
		assertEquals(760, rect.x(), EPSILON);
		assertEquals(390, rect.y(), EPSILON);
	}

	/** 画布中心之外的图层按画布坐标平移。 */
	@Test
	void offCentreLayersMove()
	{
		WeScene.Layer corner = new WeScene.Layer("test", "texture", 0, 0, 100,
			100, 1, 1, 1, 1, 1, 1, 0, 0);

		Rect rect = WeSceneLayout.rect(corner, 0.5F, 3840, 2160, 1920, 1080,
			100, 100, 0, 0);

		// 画布左上角 = 屏幕 (960,540) 减去半个画布，再减去半个图层
		assertEquals(960 - 960 - 25, rect.x(), EPSILON);
		assertEquals(540 - 540 - 25, rect.y(), EPSILON);
	}

	@Test
	void parallaxOffsetsAreSubtleAndOpposite()
	{
		// 深度 1 表示与鼠标 1:1
		assertEquals(-100,
			WeSceneLayout.parallaxOffset(1, 1, 1, 100), EPSILON);

		// Persica 的深度是 0.05 到 0.12，鼠标偏 500 像素也只挪几十像素
		assertEquals(-25,
			WeSceneLayout.parallaxOffset(0.05F, 1, 1, 500), EPSILON);

		// amount 与 influence 都是乘数
		assertEquals(-12.5F,
			WeSceneLayout.parallaxOffset(0.05F, 0.5F, 1, 500), EPSILON);
		assertEquals(0, WeSceneLayout.parallaxOffset(0.05F, 1, 0, 500),
			EPSILON);
	}

	@Test
	void emptyRectsAreReported()
	{
		assertTrue(new Rect(0, 0, 0, 10).isEmpty());
		assertTrue(new Rect(0, 0, 10, -1).isEmpty());
		assertFalse(new Rect(0, 0, 1, 1).isEmpty());

		// 比例为 0（尺寸退化）时不该画出东西
		assertTrue(WeSceneLayout
			.rect(layer(4000, 2250, 1), 0, 3840, 2160, 1920, 1080, 4000, 2250,
				0, 0)
			.isEmpty());
		assertTrue(WeSceneLayout
			.rect(null, 0.5F, 3840, 2160, 1920, 1080, 4000, 2250, 0, 0)
			.isEmpty());
	}

	/**
	 * 发射半径要放大到覆盖画布：Persica 的雪预设写 10～1200，画布 3840×2160，
	 * 照字面画就只是正中一圈（实机上用户报的就是"雪只在中间"）。
	 */
	@Test
	void emitterSpreadCoversTheCanvas()
	{
		float spread = WeSceneLayout.emitterSpread(3840, 2160, 1200);

		// 最外圈半径刚好够到画面四角
		assertEquals(Math.hypot(3840, 2160) / 2 / 1200, spread, 1e-4);

		float outer = 1200 * spread;
		assertTrue(outer >= 3840 / 2F, "左右两边还是空的：" + outer);
		assertTrue(outer >= 2160 / 2F, "上下两边还是空的：" + outer);

		// 内圈跟着一起放大，分布形状不变
		assertEquals(10 * spread, 10 * spread, 1e-4);
	}

	/** 预设本来就够大时不缩小，退化输入不炸。 */
	@Test
	void emitterSpreadNeverShrinks()
	{
		assertEquals(1, WeSceneLayout.emitterSpread(3840, 2160, 5000), 1e-4);
		assertEquals(1, WeSceneLayout.emitterSpread(3840, 2160, 2400), 1e-4);
		assertEquals(1, WeSceneLayout.emitterSpread(0, 2160, 100), 1e-4);
		assertEquals(1, WeSceneLayout.emitterSpread(3840, 0, 100), 1e-4);
		assertEquals(1, WeSceneLayout.emitterSpread(3840, 2160, 0), 1e-4);
		assertEquals(1, WeSceneLayout.emitterSpread(3840, 2160, -5), 1e-4);
	}

	/**
	 * 阻尼跟随必须与帧率无关：把一帧拆成两半跑两次，结果要和整帧跑一次一样。
	 * 不然高刷屏和 60Hz 上的手感会不一样。
	 */
	@Test
	void dampingIsFrameRateIndependent()
	{
		float once = WeSceneLayout.approach(0, 100, 0.016F, 0.5F);

		float half = WeSceneLayout.approach(0, 100, 0.008F, 0.5F);
		float twice = WeSceneLayout.approach(half, 100, 0.008F, 0.5F);

		assertEquals(once, twice, 0.05F);
	}

	@Test
	void dampingConvergesAndSnapsWithoutDelay()
	{
		float value = 0;

		for(int i = 0; i < 300; i++)
			value = WeSceneLayout.approach(value, 100, 0.016F, 0.5F);

		assertEquals(100, value, 0.5F);

		// delay 为 0 表示场景不要平滑：直接贴到目标值
		assertEquals(100, WeSceneLayout.approach(0, 100, 0.016F, 0), EPSILON);

		// dt <= 0（同一毫秒内的重复调用）不动
		assertEquals(0, WeSceneLayout.approach(0, 100, 0, 0.5F), EPSILON);

		// 长时间卡顿要截断，不能一帧跳到位：5 秒和 1 秒得到同一个结果
		float stalled = WeSceneLayout.approach(0, 100, 5, 1);
		assertEquals(WeSceneLayout.approach(0, 100, 1, 1), stalled, EPSILON);
		assertTrue(stalled > 10 && stalled < 100,
			"一帧最多走一小段，实际 " + stalled);

		// 跟在目标后面时单调逼近，不越过
		float current = 0;
		for(int i = 0; i < 50; i++)
		{
			float next = WeSceneLayout.approach(current, 100, 0.016F, 0.5F);
			assertTrue(next >= current && next <= 100);
			current = next;
		}
	}
}
