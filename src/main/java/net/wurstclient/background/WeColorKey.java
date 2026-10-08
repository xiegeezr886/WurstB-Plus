/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;

/**
 * 色键抠像（蓝幕/绿幕）。
 *
 * <p>
 * Wallpaper Engine 的场景**自己把参数写清楚了**，不需要材质或着色器系统。实测「流萤」
 * 那两层蓝幕素材的图层对象里：
 * </p>
 *
 * <pre>
 * "effects": [{"file":"effects/colorkey/effect.json",
 *   "passes":[{"constantshadervalues":{
 *       "alpha": 0,
 *       "color": "0 0.047058823529411764 1",   // 归一化 RGB
 *       "fuzziness": 0.56999999,
 *       "tolerance": 0.56999999 }}]}]
 * </pre>
 *
 * <p>
 * <b>判定用的是官方实现里的算式</b>（Wallpaper Engine 安装目录
 * {@code assets/effects/colorkey/shaders/effects/colorkey.frag} 的自述，只学数学不抄代码）：
 * 曼哈顿距离 + {@code smoothstep} 过渡 + alpha **相乘**。
 * </p>
 */
record WeColorKey(float red, float green, float blue, float tolerance,
	float fuzziness, float keyedAlpha)
{
	/** 这个效果是不是色键。 */
	private static final String EFFECT = "colorkey";

	/**
	 * 从图层的 {@code effects} 数组里找出色键参数。
	 *
	 * @return 找到并解析成功时返回参数；没有色键、或参数读不出来时返回 {@code null}
	 */
	static WeColorKey parse(JsonElement effects)
	{
		if(effects == null || !effects.isJsonArray())
			return null;

		for(JsonElement element : effects.getAsJsonArray())
		{
			if(!element.isJsonObject())
				continue;

			JsonObject effect = element.getAsJsonObject();

			if(!effect.has("file")
				|| !effect.get("file").getAsString().toLowerCase()
					.contains(EFFECT))
				continue;

			// 效果自己也带 visible：作者会用它临时关掉某个效果。实测「persica」的
			// 「backdrop flowers」上就挂着一个 visible:false 的色键 —— 不看这个字段
			// 会把作者明确禁用的效果画上去。缺字段按启用处理。
			if(!WeScene.isEffectVisible(effect))
				continue;

			JsonObject constants = firstConstants(effect);

			if(constants == null)
				continue;

			float[] color = parseColor(constants);
			float tolerance = number(constants, "tolerance", 0.5F);
			float fuzziness = number(constants, "fuzziness", 0);
			float alpha = number(constants, "alpha", 0);

			if(color == null || tolerance <= 0)
				continue;

			return new WeColorKey(color[0], color[1], color[2], tolerance,
				fuzziness, alpha);
		}

		return null;
	}

	private static JsonObject firstConstants(JsonObject effect)
	{
		if(!effect.has("passes") || !effect.get("passes").isJsonArray())
			return null;

		JsonArray passes = effect.getAsJsonArray("passes");

		if(passes.size() == 0 || !passes.get(0).isJsonObject())
			return null;

		JsonObject first = passes.get(0).getAsJsonObject();

		if(!first.has("constantshadervalues")
			|| !first.get("constantshadervalues").isJsonObject())
			return null;

		return first.getAsJsonObject("constantshadervalues");
	}

	/** {@code "r g b"}，各分量归一化到 0..1。 */
	private static float[] parseColor(JsonObject constants)
	{
		if(!constants.has("color") || !constants.get("color").isJsonPrimitive())
			return null;

		String text = constants.get("color").getAsString().trim();
		String[] parts = text.split("\\s+");

		if(parts.length < 3)
			return null;

		try
		{
			return new float[]{Float.parseFloat(parts[0]),
				Float.parseFloat(parts[1]), Float.parseFloat(parts[2])};

		}catch(NumberFormatException e)
		{
			return null;
		}
	}

	private static float number(JsonObject constants, String name,
		float fallback)
	{
		if(!constants.has(name) || !constants.get(name).isJsonPrimitive())
			return fallback;

		try
		{
			return constants.get(name).getAsFloat();

		}catch(RuntimeException e)
		{
			return fallback;
		}
	}

	/**
	 * 就地抠像：把靠近键色的像素弄透明。
	 *
	 * <p>
	 * 在**降采样之前**做：降采样是按 alpha 预乘求平均的，先把蓝底抠成透明，边缘的
	 * 过渡才不会把蓝色混进人物那一侧。
	 * </p>
	 *
	 * <p>
	 * <b>判定公式来自官方实现的自述</b>（Wallpaper Engine 安装目录里的
	 * {@code assets/effects/colorkey/shaders/effects/colorkey.frag}，只学算式、不抄代码）：
	 * </p>
	 *
	 * <pre>
	 * delta = |key.r - c.r| + |key.g - c.g| + |key.b - c.b|   // 曼哈顿距离，不是欧氏
	 * blend = smoothstep(0.001, 0.002 + fuzziness, delta - tolerance)
	 * a    *= mix(keyedAlpha, 1, blend)                        // 是乘，不是替换
	 * </pre>
	 *
	 * <p>
	 * 我原先按欧氏距离、并把 alpha 直接替换成结果 —— 两处都不对：欧氏会把某些颜色判得
	 * 比实际更靠近键色，直接替换则会丢掉图层原有的半透明（封面、淡入淡出都靠它）。
	 * </p>
	 */
	void apply(NativeImage image)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		float edge0 = 0.001F;
		float edge1 = 0.002F + fuzziness;

		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++)
			{
				// NativeImage 的整数像素接口是 ABGR：低字节是红
				int pixel = image.getPixelRGBA(x, y);
				int alpha = pixel >>> 24;

				if(alpha == 0)
					continue;

				float r = (pixel & 0xFF) / 255F;
				float g = (pixel >> 8 & 0xFF) / 255F;
				float b = (pixel >> 16 & 0xFF) / 255F;

				float delta = Math.abs(red - r) + Math.abs(green - g)
					+ Math.abs(blue - b);
				float blend =
					smoothstep(edge0, edge1, delta - tolerance);
				float factor = keyedAlpha + (1 - keyedAlpha) * blend;

				int out = Math.round(alpha * Math.max(0, Math.min(1, factor)));

				image.setPixelRGBA(x, y, out << 24 | (pixel & 0x00FFFFFF));
			}
	}

	/** GLSL 的 {@code smoothstep}：两端之外是常数，之间走三次 Hermite 过渡。 */
	private static float smoothstep(float edge0, float edge1, float x)
	{
		if(edge1 <= edge0)
			return x < edge0 ? 0 : 1;

		float u = (x - edge0) / (edge1 - edge0);
		u = Math.max(0, Math.min(1, u));
		return u * u * (3 - 2 * u);
	}
}
