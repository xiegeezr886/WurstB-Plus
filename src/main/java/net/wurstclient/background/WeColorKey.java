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
 * <b>判定用的是归一化 RGB 的欧氏距离</b>：距离在 {@code tolerance} 以内按透明处理，
 * 再往外一段渐变回不透明。这是对 WE 那个着色器数学的**推测**（拿不到它的源码），
 * 所以：① 只做保守的键出，绝不把远离键色的颜色也弄透明；② 画面要肉眼确认过才算数。
 * </p>
 *
 * <p>
 * 渐变的宽度取 {@code min(fuzziness, tolerance)}：{@code fuzziness} 具体怎么参与
 * 计算没有权威依据，这样取的含义是"在 tolerance 这段距离内完成过渡"，而当
 * {@code fuzziness >= tolerance}（实测就是这种）时退化为**整段 tolerance 都用来渐变**
 * —— 键色正中全透明、边界处刚好不透明，人物那侧不受影响。
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
	 */
	void apply(NativeImage image)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		float band = Math.min(fuzziness, tolerance);
		float opaqueAt = tolerance;
		float clearBelow = tolerance - band;

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

				float dr = r - red;
				float dg = g - green;
				float db = b - blue;
				float distance =
					(float)Math.sqrt(dr * dr + dg * dg + db * db);

				if(distance >= opaqueAt)
					continue;

				float factor;

				if(distance <= clearBelow || band <= 0)
					factor = keyedAlpha;
				else
					factor = keyedAlpha + (1 - keyedAlpha)
						* (distance - clearBelow) / band;

				int out = Math.round(alpha * Math.max(0, Math.min(1, factor)));

				image.setPixelRGBA(x, y,
					out << 24 | (pixel & 0x00FFFFFF));
			}
	}
}
