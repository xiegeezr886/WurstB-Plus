/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * 图层上的「精确模糊」（{@code effects/blurprecise}）。
 *
 * <p>
 * <b>算式依据</b>：Wallpaper Engine 安装目录里的明文着色器 ——
 * {@code assets/effects/blurprecise/effect.json}（两趟、X 后 Y）、
 * {@code .../blur_precise_gaussian.vert}（步长 {@code g_Scale / 纹理尺寸}，
 * 参数名就叫 {@code scale}，默认 {@code 1 1}）、以及核权重
 * {@code assets/shaders/**}{@code /common_blur.h}。Java 实现是照着算式自己写的，
 * 没有搬运它的代码。
 * </p>
 *
 * <p>
 * <b>与本项目的差异</b>：① 官方在图层**原始分辨率**上做，我是在**降采样之后**做
 * （降采样是为像素预算服务的）。所以实际半径会按降采样系数放大约 9%，肉眼看不出来，
 * 但写清楚免得被当成逐像素一致。② 官方支持遮罩（{@code MASK} 组合）与「不模糊 alpha」
 * （{@code BLURALPHA}），本项目都没做 —— 只做最常用的整体模糊。
 * </p>
 *
 * @param scaleX
 *            横向步长，单位是**纹素**（官方 {@code g_Scale.x}）
 * @param scaleY
 *            纵向步长，单位是纹素
 * @param kernel
 *            核大小：0 = 13×13（官方默认）、1 = 7×7、2 = 3×3
 */
record WeBlur(float scaleX, float scaleY, int kernel)
{
	/** 官方 {@code common_blur.h} 里 blur13 的 7 个抽头（相对步长的偏移与权重）。 */
	private static final float[] OFFSETS_13 =
		{0F, 1.4091998770852122F, -1.4091998770852122F, 3.2979348079914822F,
			-3.2979348079914822F, 5.2062900776825969F, -5.2062900776825969F};

	private static final float[] WEIGHTS_13 = {0.1976406528809576F,
		0.2959855056006557F, 0.2959855056006557F, 0.0935333619980593F,
		0.0935333619980593F, 0.0116608059608062F, 0.0116608059608062F};

	/** blur7 的 4 个抽头。 */
	private static final float[] OFFSETS_7 = {2.3515644035337887F,
		0.469433779698372F, -1.4091998770852121F, -3F};

	private static final float[] WEIGHTS_7 = {0.2028175528299753F,
		0.4044856614512112F, 0.3213933537319605F, 0.0713034319868530F};

	/** blur3 的 3 个抽头。 */
	private static final float[] OFFSETS_3 = {1F, 0F, -1F};
	private static final float[] WEIGHTS_3 = {0.25F, 0.5F, 0.25F};

	/**
	 * 从对象的 {@code effects} 数组里认出一个精确模糊。没有就返回 {@code null}。
	 *
	 * <p>
	 * 与色键同一套读法：效果条目里 {@code file} 指向 {@code effects/blurprecise}，
	 * 参数在 {@code passes[].constantshadervalues} 里。官方默认 {@code scale} 是
	 * {@code 1 1}、核是 13×13，所以缺参数时按默认来（不是"跳过"）。
	 * </p>
	 */
	static WeBlur parse(JsonElement effects)
	{
		if(effects == null || !effects.isJsonArray())
			return null;

		for(JsonElement element : effects.getAsJsonArray())
		{
			if(!element.isJsonObject())
				continue;

			JsonObject effect = element.getAsJsonObject();
			JsonElement file = effect.get("file");

			if(file == null || !file.isJsonPrimitive()
				|| !file.getAsString().contains("blurprecise"))
				continue;

			// **效果自己也带 visible**，作者会用它临时关掉某个效果。实测「persica」的
			// 「backdrop flowers」就是 visible:false 的模糊 —— 不看这个字段就会把作者
			// 明确禁用的效果画上去。缺字段按启用处理（与图层 visible 同一套语义）。
			if(!WeScene.isEffectVisible(effect))
				continue;

			float scaleX = 1;
			float scaleY = 1;
			int kernel = 0;

			if(effect.has("passes") && effect.get("passes").isJsonArray())
				for(JsonElement pass : effect.getAsJsonArray("passes"))
				{
					if(!pass.isJsonObject())
						continue;

					JsonObject values = pass.getAsJsonObject()
						.has("constantshadervalues")
							? pass.getAsJsonObject()
								.getAsJsonObject("constantshadervalues")
							: null;

					if(values == null)
						continue;

					if(values.has("scale"))
					{
						float[] parsed = vec2(values.get("scale"), 1, 1);
						scaleX = parsed[0];
						scaleY = parsed[1];
					}

					if(values.has("kernel"))
						kernel = (int)number(values.get("kernel"), kernel);
				}

			return new WeBlur(Math.abs(scaleX), Math.abs(scaleY),
				Math.max(0, Math.min(2, kernel)));
		}

		return null;
	}

	/**
	 * 就地做两趟可分离模糊（先横后纵，与官方 {@code effect.json} 的两趟一致）。
	 */
	void apply(NativeImage image)
	{
		if(kernel == 2 && scaleX <= 0 && scaleY <= 0)
			return;

		int width = image.getWidth();
		int height = image.getHeight();

		if(width < 2 || height < 2)
			return;

		float[] offsets = switch(kernel)
		{
			case 1 -> OFFSETS_7;
			case 2 -> OFFSETS_3;
			default -> OFFSETS_13;
		};

		float[] weights = switch(kernel)
		{
			case 1 -> WEIGHTS_7;
			case 2 -> WEIGHTS_3;
			default -> WEIGHTS_13;
		};

		pass(image, width, height, offsets, weights, scaleX, true);
		pass(image, width, height, offsets, weights, scaleY, false);
	}

	/**
	 * 一趟模糊。坐标越界时**夹到边缘**（官方纹理是 clamp 采样），这样权重和始终为 1，
	 * 边缘不会变暗。
	 */
	private static void pass(NativeImage image, int width, int height,
		float[] offsets, float[] weights, float scale, boolean horizontal)
	{
		if(scale <= 0)
			return;

		int[] source = new int[width * height];

		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++)
				source[y * width + x] = image.getPixelRGBA(x, y);

		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++)
			{
				float red = 0;
				float green = 0;
				float blue = 0;
				float alpha = 0;

				for(int tap = 0; tap < offsets.length; tap++)
				{
					int shifted = Math.round(offsets[tap] * scale);
					int sx = horizontal ? clamp(x + shifted, width) : x;
					int sy = horizontal ? y : clamp(y + shifted, height);
					int pixel = source[sy * width + sx];
					float weight = weights[tap];

					// NativeImage 的整数像素是 ABGR：低字节红
					red += (pixel & 0xFF) * weight;
					green += (pixel >> 8 & 0xFF) * weight;
					blue += (pixel >> 16 & 0xFF) * weight;
					alpha += (pixel >>> 24) * weight;
				}

				image.setPixelRGBA(x, y,
					clamp255(alpha) << 24 | clamp255(blue) << 16
						| clamp255(green) << 8 | clamp255(red));
			}
	}

	private static int clamp(int value, int limit)
	{
		return value < 0 ? 0 : value >= limit ? limit - 1 : value;
	}

	private static int clamp255(float value)
	{
		int rounded = Math.round(value);
		return rounded < 0 ? 0 : rounded > 255 ? 255 : rounded;
	}

	private static float[] vec2(JsonElement element, float defX, float defY)
	{
		if(element == null)
			return new float[]{defX, defY};

		if(element.isJsonObject())
		{
			JsonObject object = element.getAsJsonObject();
			return object.has("value")
				? vec2(object.get("value"), defX, defY) : new float[]{defX, defY};
		}

		if(!element.isJsonPrimitive())
			return new float[]{defX, defY};

		String[] parts = element.getAsString().trim().split("\\s+");

		try
		{
			float x = parts.length > 0 ? Float.parseFloat(parts[0]) : defX;
			float y = parts.length > 1 ? Float.parseFloat(parts[1]) : x;
			return new float[]{x, y};

		}catch(NumberFormatException e)
		{
			return new float[]{defX, defY};
		}
	}

	private static double number(JsonElement element, double def)
	{
		if(element == null)
			return def;

		if(element.isJsonObject())
		{
			JsonObject object = element.getAsJsonObject();
			return object.has("value") ? number(object.get("value"), def) : def;
		}

		try
		{
			return element.getAsDouble();

		}catch(RuntimeException e)
		{
			return def;
		}
	}
}
