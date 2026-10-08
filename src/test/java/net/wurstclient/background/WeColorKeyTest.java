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
 * {@link WeColorKey}：键色参数怎么读、像素怎么抠。
 *
 * <p>
 * 参数用的是「流萤」那两层的**真实 JSON**（照抄自场景文件），这样用例钉住的是实际
 * 数据形状，而不是我臆想的形状。
 * </p>
 */
final class WeColorKeyTest
{
	/** 照抄自真实场景文件。 */
	private static final String REAL = """
		[{"file":"effects/colorkey/effect.json","id":281,"name":"",
		  "passes":[{"constantshadervalues":{
		    "alpha":0,
		    "color":"0 0.047058823529411764 1",
		    "fuzziness":0.56999999,
		    "tolerance":0.56999999},"id":286}],"visible":true}]
		""";

	@Test
	void readsTheRealEffectParameters()
	{
		WeColorKey key = WeColorKey.parse(JsonParser.parseString(REAL));

		assertNotNull(key);
		assertEquals(0F, key.red(), 0.001F);
		assertEquals(0.0471F, key.green(), 0.001F);
		assertEquals(1F, key.blue(), 0.001F);
		assertEquals(0.57F, key.tolerance(), 0.001F);
		assertEquals(0.57F, key.fuzziness(), 0.001F);
		assertEquals(0F, key.keyedAlpha(), 0.001F);
	}

	/** 其它效果（比如模糊）不该被当成抠像。 */
	@Test
	void ignoresOtherEffects()
	{
		String other = """
			[{"file":"effects/blur/effect.json","passes":[
			  {"constantshadervalues":{"strength":2}}]}]
			""";

		assertNull(WeColorKey.parse(JsonParser.parseString(other)));
	}

	@Test
	void toleratesMissingOrBrokenInput()
	{
		assertNull(WeColorKey.parse(null));
		assertNull(WeColorKey.parse(JsonParser.parseString("[]")));
		assertNull(WeColorKey.parse(JsonParser.parseString("3")));

		// 有色键但没写颜色
		assertNull(WeColorKey.parse(JsonParser.parseString("""
			[{"file":"effects/colorkey/effect.json",
			  "passes":[{"constantshadervalues":{"tolerance":0.5}}]}]
			""")));

		// 容差为 0 等于不抠
		assertNull(WeColorKey.parse(JsonParser.parseString("""
			[{"file":"effects/colorkey/effect.json",
			  "passes":[{"constantshadervalues":{
			    "color":"0 0 1","tolerance":0}}]}]
			""")));
	}

	/** 纯蓝底整个透明，人物那种肤色完全不动。 */
	@Test
	void keysOutTheBlueScreenAndKeepsSkin()
	{
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, 2, 1, false);
		image.setPixelRGBA(0, 0, argb(255, 0, 12, 255)); // 蓝幕
		image.setPixelRGBA(1, 0, argb(255, 232, 190, 165)); // 肤色

		key().apply(image);

		assertEquals(0, image.getPixelRGBA(0, 0) >>> 24, "蓝幕应当被整个抠掉");
		assertEquals(255, image.getPixelRGBA(1, 0) >>> 24, "肤色不该被动");

		image.close();
	}

	/** 键色附近但要淡一些的像素走渐变，别一刀切。 */
	@Test
	void feathersTheEdge()
	{
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, 3, 1, false);

		// 距离键色：0（正中）、约 0.29（渐变带中间）、很大（不动）
		image.setPixelRGBA(0, 0, argb(255, 0, 12, 255));
		image.setPixelRGBA(1, 0, argb(255, 80, 100, 255));
		image.setPixelRGBA(2, 0, argb(255, 255, 255, 255));

		key().apply(image);

		int center = image.getPixelRGBA(0, 0) >>> 24;
		int edge = image.getPixelRGBA(1, 0) >>> 24;
		int white = image.getPixelRGBA(2, 0) >>> 24;

		assertEquals(0, center);
		assertTrue(edge > 0 && edge < 255,
			"渐变带上应当是半透明，实际 " + edge);
		assertEquals(255, white, "白色离键色很远，不该被动");

		image.close();
	}

	/** 本来就透明的像素跳过，别把 alpha 又乘一遍。 */
	@Test
	void leavesAlreadyTransparentPixelsAlone()
	{
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, 1, 1, false);
		image.setPixelRGBA(0, 0, argb(0, 0, 12, 255));

		key().apply(image);

		assertEquals(0, image.getPixelRGBA(0, 0) >>> 24);
		image.close();
	}

	/** 颜色分量不该被抠像改动 —— 只动 alpha。 */
	@Test
	void onlyTouchesAlpha()
	{
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, 1, 1, false);
		image.setPixelRGBA(0, 0, argb(200, 40, 90, 220));
		int before = image.getPixelRGBA(0, 0) & 0x00FFFFFF;

		key().apply(image);

		assertEquals(before, image.getPixelRGBA(0, 0) & 0x00FFFFFF);
		image.close();
	}

	/**
	 * 距离用**曼哈顿**而不是欧氏 —— 这是官方着色器里写死的算式
	 * （{@code dot(abs(keyColor - albedo.rgb), vec3(1,1,1))}），两者会给出不同判定。
	 *
	 * <p>
	 * 取一个能区分两者的颜色：键色 (0,0,1)、颜色约 (0.3,0.3,1)、容差 0.5 ——
	 * 曼哈顿距离是 0.6（**超出**容差、应当保留），欧氏距离只有 0.424（会被误判成键出）。
	 * 所以这条用例能钉住用的到底是哪一种。
	 * </p>
	 */
	@Test
	void usesManhattanDistanceNotEuclidean()
	{
		WeColorKey key = WeColorKey.parse(JsonParser.parseString("""
			[{"file":"effects/colorkey/effect.json",
			  "passes":[{"constantshadervalues":{
			    "color":"0 0 1","tolerance":0.5,"fuzziness":0.0,
			    "alpha":0}}]}]
			"""));

		NativeImage image = new NativeImage(NativeImage.Format.RGBA, 1, 1, false);
		image.setPixelRGBA(0, 0, argb(255, 77, 77, 255));

		key.apply(image);

		assertEquals(255, image.getPixelRGBA(0, 0) >>> 24,
			"曼哈顿距离 0.6 超出容差 0.5，这个像素不该被抠掉");

		image.close();
	}

	/** alpha 是**乘**上去的（mix(keyAlpha, 1, blend)），不是直接替换。 */
	@Test
	void multipliesTheExistingAlpha()
	{
		NativeImage image = new NativeImage(NativeImage.Format.RGBA, 2, 1, false);

		image.setPixelRGBA(0, 0, argb(200, 0, 12, 255));
		image.setPixelRGBA(1, 0, argb(64, 0, 12, 255));

		key().apply(image);

		assertEquals(0, image.getPixelRGBA(0, 0) >>> 24);
		assertEquals(0, image.getPixelRGBA(1, 0) >>> 24);

		image.close();
	}

	// ------------------------------------------------------------------

	private static WeColorKey key()
	{
		return WeColorKey.parse(JsonParser.parseString(REAL));
	}

	/** NativeImage 的整数像素接口是 ABGR。 */
	private static int argb(int a, int r, int g, int b)
	{
		return a << 24 | b << 16 | g << 8 | r;
	}
}
