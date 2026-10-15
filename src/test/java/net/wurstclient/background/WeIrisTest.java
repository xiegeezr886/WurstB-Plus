/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * 虹膜扰动（{@link WeIris}）。
 *
 * <p>
 * 参数样本是「真寻」那个对象上抄下来的原文；算式是 {@code shaders/effects/iris.vert}
 * 的直译，所以这里钉两类东西：<b>参数读得对</b>、以及<b>算式在给定时刻算出的偏移</b>
 * （t=0 那一组是我按 GLSL 逐行手算的常数，不是把实现再算一遍）。
 * </p>
 */
final class WeIrisTest
{
	/** 真寻 2989215532 的 真寻_眼2 上挂着的那一段（原文照抄）。 */
	private static final String EFFECTS = """
		[{"file":"effects/iris/effect.json","id":73,"name":"",
		  "passes":[{"constantshadervalues":{"noiseamount":0.5,"phase":0.25,
		             "rough":1,"scale":"2.5 2.5","speed":2},
		             "id":74,"textures":[null,"masks/iris_mask_6b405f50"]}],
		  "visible":{"script":"'use strict';","value":true}}]
		""";

	@Test
	void readsTheRealParameters()
	{
		WeIris iris = WeIris.parse(com.google.gson.JsonParser
			.parseString(EFFECTS));

		assertNotNull(iris);
		assertEquals(2F, iris.speed(), 0.0001F);
		assertEquals(1F, iris.rough(), 0.0001F);
		assertEquals(0.5F, iris.noiseAmount(), 0.0001F);
		assertEquals(0.25F, iris.phase(), 0.0001F);
		assertEquals(2.5F, iris.scaleX(), 0.0001F);
		assertEquals(2.5F, iris.scaleY(), 0.0001F);
		assertEquals("masks/iris_mask_6b405f50", iris.maskTexture());
	}

	/** 作者用 visible:false 关掉的效果不画（与色键同一套规矩）。 */
	@Test
	void skipsDisabledEffects()
	{
		String disabled = EFFECTS.replace("\"value\":true", "\"value\":false");

		assertNull(
			WeIris.parse(com.google.gson.JsonParser.parseString(disabled)));
	}

	@Test
	void ignoresUnrelatedEffects()
	{
		assertNull(WeIris.parse(com.google.gson.JsonParser.parseString(
			"[{\"file\":\"effects/colorkey/effect.json\",\"passes\":[]}]")));
		assertNull(WeIris.parse(null));
	}

	/**
	 * t=0 的偏移（speed=1、phase=0、noise=0、rough=1、scale=1）：
	 *
	 * <pre>
	 * lowDt=0 → motion2=(sin0, sin1.9)=(0, 0.94630)
	 *          motion4=(sin1, sin2, sin1, sin2)=(0.84147, 0.90930, …)
	 * moveStart=(0+0.84147, 0+0.90930)=(0.84147, 0.90930)
	 * frac(time)=0 → cos(0)*-0.5+0.5=0 → smoothstep(0,1,0)=0 → da=moveStart
	 * da *= scale*0.001 → (0.00084147, 0.00090930)
	 * </pre>
	 */
	@Test
	void offsetAtZeroMatchesTheHandComputedShaderValue()
	{
		WeIris iris = new WeIris(1, 1, 0, 0, 1, 1, null);
		float[] offset = iris.offset(0);

		assertEquals(0.00084147F, offset[0], 0.00001F);
		assertEquals(0.00090930F, offset[1], 0.00001F);
	}

	/** 幅度上限：{@code (2 + noiseamount) × scale × 0.001}（不是量出来的，是算式的界）。 */
	@Test
	void offsetStaysWithinTheFormulaBound()
	{
		WeIris iris = new WeIris(2, 1, 0.5F, 0.25F, 2.5F, 2.5F, null);
		float bound = (2 + 0.5F) * 2.5F * 0.001F;

		for(int step = 0; step < 400; step++)
		{
			float[] offset = iris.offset(step * 0.05);
			assertTrue(Math.abs(offset[0]) <= bound + 1e-5,
				"offset[0]=" + offset[0]);
			assertTrue(Math.abs(offset[1]) <= bound + 1e-5,
				"offset[1]=" + offset[1]);
		}
	}

	/** 遮罩全 0：一个像素都不动（片元着色器里 {@code uv + da*mask} 的 mask=0）。 */
	@Test
	void zeroMaskLeavesTheImageUntouched()
	{
		NativeImage source = dot(64, 64, 5, 5);
		NativeImage mask = fill(64, 64, 0);
		NativeImage out = blank(64, 64);

		new WeIris(1, 1, 0, 0, 1000, 1000, null).apply(source, mask, out, 0);

		for(int y = 0; y < 64; y++)
			for(int x = 0; x < 64; x++)
				assertEquals(source.getPixelRGBA(x, y), out.getPixelRGBA(x, y),
					"pixel " + x + "," + y);

		source.close();
		mask.close();
		out.close();
	}

	/**
	 * 遮罩全 1 且 scale=1000：整层按 {@code round(offset × 尺寸)} 平移。
	 *
	 * <p>
	 * 方向要说清楚：着色器是 {@code tex(uv + da)}，所以**内容是往 -da 走** ——
	 * t=0 的 offset 是上面那两个常数，乘 100 像素取整为 (84, 91)，于是源图 (95,99)
	 * 的那个点会出现在输出的 (95-84, 99-91) = (11, 8)。
	 * </p>
	 */
	@Test
	void fullMaskShiftsTheWholeImage()
	{
		NativeImage source = dot(100, 100, 95, 99);
		NativeImage mask = fill(100, 100, 255);
		NativeImage out = blank(100, 100);

		new WeIris(1, 1, 0, 0, 1000, 1000, null).apply(source, mask, out, 0);

		assertEquals(source.getPixelRGBA(95, 99), out.getPixelRGBA(11, 8),
			"白点应当沿 -da 方向移到 (95-84, 99-91)");
		assertEquals(0, out.getPixelRGBA(95, 99), "原位置应当空了");

		source.close();
		mask.close();
		out.close();
	}

	/** 没有遮罩 = 整层统一偏移（片元着色器里 MASK 不成立的那条分支）。 */
	@Test
	void missingMaskShiftsUniformly()
	{
		NativeImage source = dot(100, 100, 95, 99);
		NativeImage out = blank(100, 100);

		new WeIris(1, 1, 0, 0, 1000, 1000, null).apply(source, null, out, 0);

		assertEquals(source.getPixelRGBA(95, 99), out.getPixelRGBA(11, 8));

		source.close();
		out.close();
	}

	/** 平移会把边缘露出来，采样必须夹取（不能出现黑边或环绕）。 */
	@Test
	void clampsAtTheEdges()
	{
		NativeImage source = fill(16, 16, 200);
		NativeImage out = blank(16, 16);

		new WeIris(1, 1, 0, 0, -1000, -1000, null).apply(source, null, out, 0);

		assertEquals(200 & 0xFF, out.getPixelRGBA(0, 0) & 0xFF);

		source.close();
		out.close();
	}

	private static NativeImage blank(int width, int height)
	{
		return new NativeImage(NativeImage.Format.RGBA, width, height, false);
	}

	/** 全 0，只有一个灰白的点（用来观察平移）。 */
	private static NativeImage dot(int width, int height, int x, int y)
	{
		NativeImage image = blank(width, height);
		image.setPixelRGBA(x, y, 0xFF808080);
		return image;
	}

	/** NativeImage 的整数像素是 ABGR，低字节是红 —— 遮罩取的就是这一路。 */
	private static NativeImage fill(int width, int height, int red)
	{
		NativeImage image = blank(width, height);

		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++)
				image.setPixelRGBA(x, y, 0xFF000000 | red);

		return image;
	}
}
