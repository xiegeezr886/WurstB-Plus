/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * {@link LayerResampler}：什么时候敢降、降多少、降出来像素对不对。
 *
 * <p>
 * 这里最要紧的一条是 {@link #refusesWhenTheSceneDoesNotDeclareASize()} —— 场景没声明
 * 尺寸时布局是拿贴图尺寸当显示尺寸的，降了会把图层画小，所以必须拒绝。
 * </p>
 */
final class LayerResamplerTest
{
	/** 场景没声明尺寸（sizeX/sizeY 为 0）时一律不许降。 */
	@Test
	void refusesWhenTheSceneDoesNotDeclareASize()
	{
		assertNull(LayerResampler.target(2000, 2000, 0, 0, 1, 1));
		assertNull(LayerResampler.target(2000, 2000, -1, 100, 1, 1));
		assertNull(LayerResampler.target(2000, 2000, 100, 0, 1, 1));

		// 即使 scale 很小（看起来浪费巨大）也不许动：布局这时拿贴图尺寸当尺寸
		assertNull(LayerResampler.target(2000, 2000, 0, 0, 0.01F, 0.01F));
	}

	/** 贴图不比显示尺寸大出 MIN_FACTOR 倍就不值得降。 */
	@Test
	void leavesSmallTexturesAlone()
	{
		// 显示 500、余量 1.25 => 目标 625，贴图 1000 没到 2 倍
		assertNull(LayerResampler.target(1000, 1000, 500, 500, 1, 1));

		// 贴图刚好等于显示尺寸
		assertNull(LayerResampler.target(100, 100, 100, 100, 1, 1));

		// 贴图比显示尺寸还小（会被放大画），当然不降
		assertNull(LayerResampler.target(50, 50, 100, 100, 1, 1));
	}

	/**
	 * 实测的真实浪费：某场景的钟表圆点 {@code tex=2048x2048 size=2000x2000
	 * scale=0.1}，屏幕上只有 200 见方 —— 2048 对 200，**105 倍**的浪费。
	 *
	 * <p>
	 * 这个用例同时钉住"显示尺寸必须算上 scale"：只看 size 的话 2048 并不比 2000 大，
	 * 会被判成"没超"，也就永远不会降（第一版就是这么漏掉的）。
	 * </p>
	 */
	@Test
	void shrinksTheOversizedClockDot()
	{
		int[] target =
			LayerResampler.target(2048, 2048, 2000, 2000, 0.1F, 0.1F);

		assertNotNull(target, "显示 200、余量 1.25 => 目标 250，2048 远超 2 倍");

		// 倍率 = 2048 / 250 = 8.192
		assertEquals(250, target[0]);
		assertEquals(250, target[1]);

		// 4.19M 像素 -> 62.5k 像素，省下 98.5%
		assertEquals(62_500, target[0] * target[1]);
	}

	/** 同一场景的指针：{@code tex=2048x2048 size=2000x2000 scale=0.25} -> 显示 500。 */
	@Test
	void shrinksTheOversizedClockHand()
	{
		int[] target =
			LayerResampler.target(2048, 2048, 2000, 2000, 0.25F, 0.25F);

		assertNotNull(target);
		assertEquals(625, target[0]);
		assertEquals(625, target[1]);
	}

	/** 倍率有上限，再离谱的素材也不会被缩成 1x1。 */
	@Test
	void capsTheReductionFactor()
	{
		int[] target = LayerResampler.target(8000, 4000, 10, 10, 1, 1);

		assertNotNull(target);
		// 目标边长 12.5，本来要缩 640 倍，被 MAX_FACTOR=16 挡住
		assertEquals(500, target[0]);
		assertEquals(250, target[1]);
	}

	/** 长宽比要保持，不能把图拉扁。 */
	@Test
	void keepsTheAspectRatio()
	{
		int[] target = LayerResampler.target(4096, 2048, 100, 100, 1, 1);

		assertNotNull(target);
		assertEquals(2.0, target[0] / (double)target[1], 0.02);
	}

	/** 正常的 4K 背景层不该被动：贴图 4096、显示 3840，只超一点点。 */
	@Test
	void leavesAProperlySizedBackgroundAlone()
	{
		assertNull(LayerResampler.target(4096, 4096, 3840, 2160, 1, 1));
	}

	// ------------------------------- 塞不进剩余预算时按缺口再缩 ------------------

	/** 放得下就什么都不做。 */
	@Test
	void fitIntoLeavesRoomEnoughAlone()
	{
		assertNull(LayerResampler.fitInto(1000, 1000, 1_000_000));
		assertNull(LayerResampler.fitInto(1000, 1000, 2_000_000));
	}

	/**
	 * 只差一点点时也要缩，哪怕只缩掉一个像素 —— 缩一点点留住这一层，
	 * 远好过因为差几个像素就把整层丢掉。
	 */
	@Test
	void fitIntoShrinksEvenForATinyDeficit()
	{
		int[] fit = LayerResampler.fitInto(1000, 1000, 1_000_000 - 1);

		assertNotNull(fit);
		assertTrue((long)fit[0] * fit[1] <= 999_999, "必须真的塞得下");
	}

	/** 剩余空间只有四分之一时应当缩到一半边长。 */
	@Test
	void fitIntoHalvesTheEdgeWhenRoomIsAQuarter()
	{
		int[] fit = LayerResampler.fitInto(1000, 1000, 250_000);

		assertNotNull(fit);
		assertEquals(500, fit[0]);
		assertEquals(500, fit[1]);
		// 缩完必须真的塞得下
		assertTrue((long)fit[0] * fit[1] <= 250_000);
	}

	/** 长宽比要保持。 */
	@Test
	void fitIntoKeepsTheAspectRatio()
	{
		int[] fit = LayerResampler.fitInto(4096, 2048, 1_000_000);

		assertNotNull(fit);
		assertEquals(2.0, fit[0] / (double)fit[1], 0.02);
	}

	/** 剩余空间小到会突破质量地板时，宁可返回 null 去丢层。 */
	@Test
	void fitIntoRefusesToGoBelowTheQualityFloor()
	{
		// 4096x4096 塞进 1000 像素：就算缩到 1x1 也不够……
		assertNull(LayerResampler.fitInto(4096, 4096, 1000));

		// 剩余空间本身就是地板以下
		assertNull(LayerResampler.fitInto(1000, 1000,
			LayerResampler.MIN_PIXELS - 1));
	}

	/** 缩到地板之上但刚好够放时应当放行 —— 别把地板当成"必须大于"。 */
	@Test
	void fitIntoAllowsExactlyTheFloor()
	{
		int[] fit = LayerResampler.fitInto(2048, 2048, 70_000);

		assertNotNull(fit);
		assertTrue((long)fit[0] * fit[1] >= LayerResampler.MIN_PIXELS,
			"不该缩到地板以下");
		assertTrue((long)fit[0] * fit[1] <= 70_000, "又必须真的塞得下");
	}

	/** 面积平均：4x4 每个 2x2 块取平均。 */
	@Test
	void averagesEachBlock()
	{
		NativeImage source = image(4, 4);

		// 四个 2x2 块分别填 0 / 100 / 200 / 250 的灰度
		fill(source, 0, 0, 2, 2, 255, 0, 0, 0);
		fill(source, 2, 0, 2, 2, 255, 100, 100, 100);
		fill(source, 0, 2, 2, 2, 255, 200, 200, 200);
		fill(source, 2, 2, 2, 2, 255, 250, 250, 250);

		NativeImage out = LayerResampler.downscale(source, 2, 2);

		assertEquals(2, out.getWidth());
		assertEquals(2, out.getHeight());
		assertPixel(out, 0, 0, 255, 0, 0, 0);
		assertPixel(out, 1, 0, 255, 100, 100, 100);
		assertPixel(out, 0, 1, 255, 200, 200, 200);
		assertPixel(out, 1, 1, 255, 250, 250, 250);

		source.close();
		out.close();
	}

	/**
	 * 半透明的平均值要按 alpha 预乘算 —— 否则一格透明、一格纯色时会算出"半透明的
	 * 纯色"，边缘会出现脏色。
	 */
	@Test
	void averagesTransparencyWithoutBleedingColour()
	{
		NativeImage source = image(2, 1);

		// 一格完全透明（颜色随便填红），一格不透明的绿
		source.setPixelRGBA(0, 0, 0x00FF0000);
		source.setPixelRGBA(1, 0, 0xFF00FF00);

		NativeImage out = LayerResampler.downscale(source, 1, 1);

		// alpha 平均 = (0 + 255) / 2 = 127
		int pixel = out.getPixelRGBA(0, 0);
		assertEquals(127, pixel >>> 24, "alpha 应当是两者的平均");

		// 预乘之后透明那格的红色权重是 0，所以颜色应当还是绿，不该混进红
		assertEquals(255, pixel >> 8 & 0xFF, "绿通道");
		assertEquals(0, pixel & 0xFF, "红通道不该被透明格污染");
		assertEquals(0, pixel >> 16 & 0xFF, "蓝通道");

		source.close();
		out.close();
	}

	@Test
	void refusesNonsensicalTargets()
	{
		NativeImage source = image(4, 4);

		assertThrows(IllegalArgumentException.class,
			() -> LayerResampler.downscale(source, 0, 2));
		assertThrows(IllegalArgumentException.class,
			() -> LayerResampler.downscale(source, 8, 8));

		source.close();
	}

	/** 不是整数倍时也要覆盖到所有源像素，不能漏掉边上一列。 */
	@Test
	void coversEverySourcePixelWhenNotAnExactMultiple()
	{
		NativeImage source = image(5, 1);

		for(int x = 0; x < 5; x++)
			source.setPixelRGBA(x, 0, 0xFF000000 | x * 10);

		// 5 -> 2：切分是 [0,2) 与 [2,5)，也就是覆盖 0,1（均值 5）与 2,3,4（均值 30）。
		// 这个切分完整且不重叠：每个源像素恰好属于一个目标像素，边上不会漏。
		NativeImage out = LayerResampler.downscale(source, 2, 1);

		assertEquals(5, out.getPixelRGBA(0, 0) & 0xFF);
		assertEquals(30, out.getPixelRGBA(1, 0) & 0xFF);

		source.close();
		out.close();
	}

	// ------------------------------------------------------------------

	private static NativeImage image(int width, int height)
	{
		return new NativeImage(NativeImage.Format.RGBA, width, height, false);
	}

	private static void fill(NativeImage image, int x, int y, int width,
		int height, int a, int r, int g, int b)
	{
		for(int dy = 0; dy < height; dy++)
			for(int dx = 0; dx < width; dx++)
				image.setPixelRGBA(x + dx, y + dy,
					a << 24 | b << 16 | g << 8 | r);
	}

	private static void assertPixel(NativeImage image, int x, int y, int a,
		int r, int g, int b)
	{
		assertArrayEquals(new int[]{r, g, b, a},
			unpack(image.getPixelRGBA(x, y)), "像素 (" + x + "," + y + ")");
	}

	private static int[] unpack(int pixel)
	{
		return new int[]{pixel & 0xFF, pixel >> 8 & 0xFF, pixel >> 16 & 0xFF,
			pixel >>> 24};
	}
}
