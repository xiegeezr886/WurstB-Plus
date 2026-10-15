/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;

/**
 * 虹膜扰动（{@code effects/iris}）—— 让眼睛"活"起来的那点抖动。
 *
 * <p>
 * 参数同样写在场景对象自己身上（和 {@link WeColorKey}、{@link WeBlur} 一个套路），
 * 实测「真寻」的 {@code 真寻_眼2}：</p>
 *
 * <pre>
 * "effects": [{"file":"effects/iris/effect.json",
 *   "passes":[{"constantshadervalues":{"noiseamount":0.5,"phase":0.25,"rough":1,
 *                                     "scale":"2.5 2.5","speed":2},
 *              "textures":[null,"masks/iris_mask_6b405f50"]}]}]
 * </pre>
 *
 * <p>
 * <b>算式逐行照抄作品包里的 {@code shaders/effects/iris.vert}（不是自己编的）：</b></p>
 *
 * <pre>
 * time   = g_Time * g_Speed + g_PhaseOffset
 * lowDt  = floor(time)
 * motion2 = sin(1.9 * (lowDt + vec2(0,1)))
 * motion4 = sin(2.5 * (lowDt + vec4(0,0,1,1)) + vec4(1,2,1,2))
 * moveStart = motion2.xx + motion4.xy
 * moveEnd   = motion2.yy + motion4.zw
 * da = mix(moveStart, moveEnd,
 *          smoothstep(1 - g_Rough, 1, cos(frac(time) * PI) * -0.5 + 0.5))
 * da.x += sin(time) * g_NoiseAmount
 * da.y += cos(time) * g_NoiseAmount
 * da *= g_Scale * 0.001
 * </pre>
 *
 * <p>
 * 它算出来的 {@code da} 是**整层统一**的 UV 偏移（顶点着色器里不依赖顶点位置），
 * 到片元着色器里按遮罩逐像素加权：{@code albedo = tex(g_Texture0, uv + da * mask)}
 * —— 遮罩为 0 的地方不动，遮罩为 1 的地方整段偏移。</p>
 *
 * <p>
 * <b>幅度有多少（先说清楚，免得当成大修）</b>：{@code |da| ≤ (2 + noiseamount) × scale
 * × 0.001}，实测参数下是 {@code 2.5 × 0.001 × 2.5 = 0.00625} UV，在 774×874 的眼睛层上
 * 约 **±5 像素**，投到 1920 宽的屏上约 **±2 像素**。所以这是个"虹膜轻轻游动"的细节，
 * 不是能一眼看出的错位；而且它**必须动**才有意义 —— 冻结在某一时刻等于没有效果，
 * 所以渲染端按约 5Hz 重算并重传这张贴图。</p>
 */
record WeIris(float speed, float rough, float noiseAmount, float phase,
	float scaleX, float scaleY, String maskTexture)
{
	/** 效果文件里的关键字。 */
	private static final String EFFECT = "iris";

	/** {@code da *= g_Scale * 0.001} 里的那个 0.001。 */
	private static final float SCALE_UNIT = 0.001F;

	/**
	 * 从图层的 {@code effects} 数组里找出虹膜扰动。
	 *
	 * @return 没有这个效果、或作者把它 visible 关掉时返回 {@code null}
	 */
	static WeIris parse(JsonElement effects)
	{
		if(effects == null || !effects.isJsonArray())
			return null;

		for(JsonElement element : effects.getAsJsonArray())
		{
			if(!element.isJsonObject())
				continue;

			JsonObject effect = element.getAsJsonObject();

			if(!effect.has("file") || !effect.get("file").getAsString()
				.toLowerCase(java.util.Locale.ROOT).contains(EFFECT))
				continue;

			// 与色键同一套规矩：作者用 visible 关掉的效果不画
			if(!WeScene.isEffectVisible(effect))
				continue;

			JsonArray passes = effect.has("passes")
				&& effect.get("passes").isJsonArray()
					? effect.getAsJsonArray("passes") : new JsonArray();

			for(JsonElement passElement : passes)
			{
				if(!passElement.isJsonObject())
					continue;

				JsonObject pass = passElement.getAsJsonObject();
				JsonObject values = pass.has("constantshadervalues")
					&& pass.get("constantshadervalues").isJsonObject()
						? pass.getAsJsonObject("constantshadervalues")
						: new JsonObject();

				String mask = maskTexture(pass);

				return new WeIris((float)number(values, "speed", 1),
					(float)number(values, "rough", 0.2),
					(float)number(values, "noiseamount", 0.5),
					(float)number(values, "phase", 0),
					scaleX(values), scaleY(values), mask);
			}

			// 有 iris 效果但没有 passes：按默认参数算，遮罩无（=整层统一偏移）
			return new WeIris(1, 0.2F, 0.5F, 0, 1, 1, null);
		}

		return null;
	}

	/** {@code "scale" : "2.5 2.5"}（也可能只写一个数）。 */
	private static float scaleX(JsonObject values)
	{
		return scale(values, 0, 1);
	}

	private static float scaleY(JsonObject values)
	{
		return scale(values, 1, scale(values, 0, 1));
	}

	private static float scale(JsonObject values, int index, float fallback)
	{
		if(!values.has("scale"))
			return fallback;

		JsonElement element = values.get("scale");

		if(element.isJsonArray() && element.getAsJsonArray().size() > index)
			return element.getAsJsonArray().get(index).getAsFloat();

		if(element.isJsonPrimitive())
		{
			String[] parts = element.getAsString().trim().split("\\s+");

			if(parts.length > index)
				try
				{
					return Float.parseFloat(parts[index]);

				}catch(NumberFormatException ignored)
				{
					return fallback;
				}

			if(index == 0)
				try
				{
					return Float.parseFloat(parts[0]);

				}catch(NumberFormatException | ArrayIndexOutOfBoundsException ignored)
				{
					return fallback;
				}
		}

		return fallback;
	}

	/** {@code textures:[null,"masks/…"]} 里的第二个槽位就是遮罩。 */
	private static String maskTexture(JsonObject pass)
	{
		if(!pass.has("textures") || !pass.get("textures").isJsonArray())
			return null;

		for(JsonElement element : pass.getAsJsonArray("textures"))
			if(element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
			{
				String name = element.getAsString();
				return name.isBlank() ? null : name;
			}

		return null;
	}

	private static double number(JsonObject parent, String key, double fallback)
	{
		if(parent == null || !parent.has(key))
			return fallback;

		JsonElement element = parent.get(key);

		if(element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber())
			return element.getAsDouble();

		if(element.isJsonPrimitive())
			try
			{
				return Double.parseDouble(element.getAsString());

			}catch(NumberFormatException ignored)
			{
				return fallback;
			}

		return fallback;
	}

	/**
	 * 时间（秒）→ UV 偏移，逐行对应上面那段顶点着色器。
	 *
	 * @return {@code {offsetU, offsetV}}
	 */
	float[] offset(double seconds)
	{
		float time = (float)(seconds * speed) + phase;
		float lowDt = (float)Math.floor(time);

		float motion2x = sin(1.9F * lowDt);
		float motion2y = sin(1.9F * (lowDt + 1));
		float motion4x = sin(2.5F * lowDt + 1);
		float motion4y = sin(2.5F * lowDt + 2);
		float motion4z = sin(2.5F * (lowDt + 1) + 1);
		float motion4w = sin(2.5F * (lowDt + 1) + 2);

		float startX = motion2x + motion4x;
		float startY = motion2x + motion4y;
		float endX = motion2y + motion4z;
		float endY = motion2y + motion4w;

		float blend = smoothstep(1 - rough, 1,
			(float)(Math.cos((time - lowDt) * Math.PI) * -0.5 + 0.5));

		float x = startX + (endX - startX) * blend;
		float y = startY + (endY - startY) * blend;

		x += sin(time) * noiseAmount;
		y += cos(time) * noiseAmount;

		return new float[]{x * scaleX * SCALE_UNIT, y * scaleY * SCALE_UNIT};
	}

	private static float sin(float value)
	{
		return (float)Math.sin(value);
	}

	private static float cos(float value)
	{
		return (float)Math.cos(value);
	}

	/** GLSL 的 {@code smoothstep}。 */
	private static float smoothstep(float edge0, float edge1, float x)
	{
		if(edge0 == edge1)
			return x < edge0 ? 0 : 1;

		float t = Math.max(0,
			Math.min(1, (x - edge0) / (edge1 - edge0)));
		return t * t * (3 - 2 * t);
	}

	/**
	 * 片元着色器那一步的 CPU 版：{@code dst(x,y) = src(x + du*m, y + dv*m)}，
	 * {@code m} 是遮罩在该像素的红通道（没有遮罩时取 1，即整层一起偏移）。
	 *
	 * <p>
	 * 采样用**就近 + 边缘夹取**（着色器里也是夹取：效果材质的贴图 uv 模式是 clamp）。
	 * 越界的采样点夹到边缘，所以不会出现黑边或环绕。
	 * </p>
	 *
	 * @param mask
	 *            遮罩图；{@code null} 表示整层统一偏移
	 * @param out
	 *            目标图（与 {@code source} 同尺寸；允许就是 {@code source} 之外的缓冲）
	 * @param source
	 *            原始像素（已经做完了木偶变形等前置处理的那张）
	 */
	void apply(NativeImage source, NativeImage mask, NativeImage out,
		double seconds)
	{
		float[] offset = offset(seconds);
		int width = source.getWidth();
		int height = source.getHeight();
		int maskWidth = mask == null ? 0 : mask.getWidth();
		int maskHeight = mask == null ? 0 : mask.getHeight();

		for(int y = 0; y < height; y++)
			for(int x = 0; x < width; x++)
			{
				float weight = 1;

				if(mask != null && maskWidth > 0 && maskHeight > 0)
				{
					int mx = Math.min(maskWidth - 1, x * maskWidth / width);
					int my = Math.min(maskHeight - 1, y * maskHeight / height);
					int pixel = mask.getPixelRGBA(mx, my);
					weight = (pixel & 0xFF) / 255F; // NativeImage 是 ABGR：低字节 = 红
				}

				int sx = x + Math.round(offset[0] * weight * width);
				int sy = y + Math.round(offset[1] * weight * height);

				sx = Math.max(0, Math.min(width - 1, sx));
				sy = Math.max(0, Math.min(height - 1, sy));

				out.setPixelRGBA(x, y, source.getPixelRGBA(sx, sy));
			}
	}
}
