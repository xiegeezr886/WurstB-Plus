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
		assertNull(LayerResampler.target(2000, 2000, 0, 0));
		assertNull(LayerResampler.target(2000, 2000, -1, 100));
		assertNull(LayerResampler.target(2000, 2000, 100, 0));
	}

	/** 贴图不比显示尺寸大出 MIN_FACTOR 倍就不值得降。 */
	@Test
	void leavesSmallTexturesAlone()
	{
		// 显示 500x500、安全系数 2 => 目标 1000，贴图 1000 没到 2 倍
		assertNull(LayerResampler.target(1000, 1000, 500, 500));

		// 贴图刚好等于显示尺寸
		assertNull(LayerResampler.target(100, 100, 100, 100));

		// 贴图比显示尺寸还小（会被放大画），当然不降
		assertNull(LayerResampler.target(50, 50, 100, 100));
	}

	/** 那根"钟表指针用 2000x2000"的贴图：显示只有 100 画布单位。 */
	@Test
	void shrinksTheOversizedClockHand()
	{
		int[] target = LayerResampler.target(2000, 2000, 100, 100);

		assertNotNull(target);
		// 目标边长 = 100 * 2 = 200，倍率 2000/200 = 10
		assertEquals(200, target[0]);
		assertEquals(200, target[1]);

		// 4M 像素 -> 40k 像素，省下 99%
		assertEquals(40_000, target[0] * target[1]);
	}

	/** 倍率有上限，再离谱的素材也不会被缩成 1x1。 */
	@Test
	void capsTheReductionFactor()
	{
		int[] target = LayerResampler.target(8000, 4000, 10, 10);

		assertNotNull(target);
		// 目标边长 20，本来要缩 400 倍，被 MAX_FACTOR=16 挡住
		assertEquals(500, target[0]);
		assertEquals(250, target[1]);
	}

	/** 长宽比要保持，不能把图拉扁。 */
	@Test
	void keepsTheAspectRatio()
	{
		int[] target = LayerResampler.target(4096, 2048, 100, 100);

		assertNotNull(target);
		assertEquals(2.0, target[0] / (double)target[1], 0.02);
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
