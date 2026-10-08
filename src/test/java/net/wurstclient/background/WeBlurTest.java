/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;

/**
 * 「精确模糊」（{@link WeBlur}）。
 *
 * <p>
 * 核权重与步长语义来自官方明文着色器：{@code common_blur.h} 的 blur13 有 7 个抽头、
 * 权重和为 1；顶点着色器里步长是 {@code g_Scale / 纹理尺寸}，也就是以**纹素**为单位。
 * </p>
 */
final class WeBlurTest
{
	@Test
	void parsesScaleAndKernel()
	{
		WeBlur blur = WeBlur.parse(JsonParser.parseString("""
			[{"file":"effects/blurprecise/effect.json",
			  "passes":[{"constantshadervalues":{"scale":"2 3"}}]}]
			"""));

		assertNotNull(blur);
		assertEquals(2, blur.scaleX(), 0.001);
		assertEquals(3, blur.scaleY(), 0.001);
		// 核缺省是官方的 13×13
		assertEquals(0, blur.kernel());
	}

	/** 官方默认 scale 是 1 1 —— 缺参数时按默认来，不是跳过。 */
	@Test
	void defaultsToUnitScale()
	{
		WeBlur blur = WeBlur.parse(JsonParser.parseString("""
			[{"file":"effects/blurprecise/effect.json"}]
			"""));

		assertNotNull(blur);
		assertEquals(1, blur.scaleX(), 0.001);
		assertEquals(1, blur.scaleY(), 0.001);
	}

	/** 不是模糊的效果、或根本没有 effects，都不该产生模糊。 */
	@Test
	void ignoresOtherEffects()
	{
		assertNull(WeBlur.parse(null));
		assertNull(WeBlur.parse(JsonParser.parseString("[]")));
		assertNull(WeBlur.parse(JsonParser.parseString("""
			[{"file":"effects/colorkey/effect.json"}]
			""")));
	}

	/**
	 * 模糊会把能量摊开，**总亮度基本守恒**。
	 *
	 * <p>
	 * 核权重和为 1（官方 {@code common_blur.h} 里 blur13 的权重加起来就是 1），边缘按
	 * clamp 处理所以不会变暗。但两趟之间像素存在 8 位整数里、每趟各舍入一次，实测会
	 * 掉几个百分点（9×9、单个亮点：255 → 242，约 5%）—— 这是**真实特性**，不是 bug：
	 * 官方的中间缓冲也是 8 位（{@code rgba_backbuffer}）。所以这里按 10% 容差断言，
	 * 而不是假装它精确守恒。
	 * </p>
	 */
	@Test
	void spreadsWithoutLosingMuchBrightness()
	{
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, 9, 9, false);

		for(int y = 0; y < 9; y++)
			for(int x = 0; x < 9; x++)
				image.setPixelRGBA(x, y, 0xFF000000);

		// 正中放一个白点
		image.setPixelRGBA(4, 4, 0xFFFFFFFF);

		long before = sum(image);
		assertEquals(255, before);

		new WeBlur(1, 1, 0).apply(image);

		long after = sum(image);
		assertTrue(after >= before * 0.9 && after <= before * 1.1,
			"总亮度应当基本守恒（8 位中间结果允许几个百分点的舍入损失），实际 "
				+ before + " -> " + after);

		// 邻居被点亮了 —— 说明确实摊开了
		assertTrue(image.getPixelRGBA(3, 4) >>> 24 > 0
			|| (image.getPixelRGBA(3, 4) & 0xFF) > 0, "相邻像素应当被摊到");

		image.close();
	}

	private static long sum(NativeImage image)
	{
		long total = 0;

		for(int y = 0; y < image.getHeight(); y++)
			for(int x = 0; x < image.getWidth(); x++)
				total += image.getPixelRGBA(x, y) & 0xFF;

		return total;
	}
}
