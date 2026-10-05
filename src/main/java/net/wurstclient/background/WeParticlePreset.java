/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.io.IOException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.wurstclient.util.json.JsonUtils;

/**
 * Wallpaper Engine 的粒子预设（{@code particles/presets/*.json}）。
 *
 * <p>
 * 只读渲染雪这类 sprite 粒子需要的那部分：发射速率与范围、初速度、寿命、尺寸、
 * 颜色、摆动与淡入，以及上限。预设的字段名是「组件名 + 参数」的写法
 * （{@code emitter} / {@code initializer} / {@code operator} / {@code renderer}
 * 四个数组），所以解析时按 {@code name} 找组件，缺哪个就用默认值。</p>
 *
 * <p>
 * Persica 的两层雪（{@code snowflat} / {@code snowperspective}）实测：
 * {@code sphererandom} 发射、速率 15/秒、寿命 15～23 秒、尺寸 2～30、速度
 * x∈[-37,-10] y∈[-90,-50]（画布单位/秒，y 向下所以是往下飘）、颜色 95～255、
 * 摆动 0.8～1.0Hz、淡入 0.1 秒、上限 100000。</p>
 */
public record WeParticlePreset(float rate, float distanceMin,
	float distanceMax, float lifetimeMin, float lifetimeMax, float sizeMin,
	float sizeMax, float velocityMinX, float velocityMinY, float velocityMaxX,
	float velocityMaxY, float colorMin, float colorMax, float fadeInTime,
	float swayFrequencyMin, float swayFrequencyMax, float swayPhaseMin,
	float swayPhaseMax, float swayScaleMin, float swayScaleMax,
	float swayMaskX, float swayMaskY, int maxCount, float startTime)
{
	/** 没有发射速率时一个粒子都不发，比按默认值乱喷安全。 */
	private static final float DEFAULT_RATE = 0;

	public static WeParticlePreset parse(String json) throws IOException
	{
		if(json == null || json.isBlank())
			throw new IOException("粒子预设是空的");

		JsonObject root;

		try
		{
			root = JsonUtils.GSON.fromJson(json, JsonObject.class);
		}catch(RuntimeException e)
		{
			throw new IOException("粒子预设不是合法 JSON", e);
		}

		if(root == null)
			throw new IOException("粒子预设解析结果为空");

		float rate = DEFAULT_RATE;
		float distanceMin = 0;
		float distanceMax = 0;
		int maxCount = 1000;
		float startTime = 0;

		for(JsonElement element : array(root, "emitter"))
		{
			JsonObject emitter = object(element);

			if(emitter == null)
				continue;

			rate = (float)number(emitter, "rate", rate);
			distanceMin = (float)number(emitter, "distancemin", distanceMin);
			distanceMax = (float)number(emitter, "distancemax", distanceMax);
		}

		float lifetimeMin = 1;
		float lifetimeMax = 1;
		float sizeMin = 1;
		float sizeMax = 1;
		float[] velocityMin = {0, 0};
		float[] velocityMax = {0, 0};
		float colorMin = 255;
		float colorMax = 255;

		for(JsonElement element : array(root, "initializer"))
		{
			JsonObject initializer = object(element);

			if(initializer == null)
				continue;

			switch(string(initializer, "name"))
			{
				case "lifetimerandom" -> {
					lifetimeMin = (float)number(initializer, "min", lifetimeMin);
					lifetimeMax = (float)number(initializer, "max", lifetimeMax);
				}
				case "sizerandom" -> {
					sizeMin = (float)number(initializer, "min", sizeMin);
					sizeMax = (float)number(initializer, "max", sizeMax);
				}
				case "velocityrandom" -> {
					velocityMin = numbers(initializer, "min", 0, 0);
					velocityMax = numbers(initializer, "max", 0, 0);
				}
				case "colorrandom" -> {
					// 颜色给的是 "r g b"，这里只取亮度用（贴图是白色的圆点）
					float[] low = numbers(initializer, "min", 255, 255);
					float[] high = numbers(initializer, "max", 255, 255);
					colorMin = Math.min(low[0], low[1]);
					colorMax = Math.max(high[0], high[1]);
				}
				default -> {
				}
			}
		}

		float fadeInTime = 0;
		float swayFrequencyMin = 0;
		float swayFrequencyMax = 0;
		float swayPhaseMin = 0;
		float swayPhaseMax = 0;
		float swayScaleMin = 0;
		float swayScaleMax = 0;
		float swayMaskX = 0;
		float swayMaskY = 0;

		for(JsonElement element : array(root, "operator"))
		{
			JsonObject operator = object(element);

			if(operator == null)
				continue;

			if(!"oscillateposition".equals(string(operator, "name")))
			{
				if("alphafade".equals(string(operator, "name")))
					fadeInTime =
						(float)number(operator, "fadeintime", fadeInTime);
				continue;
			}

			swayFrequencyMin =
				(float)number(operator, "frequencymin", swayFrequencyMin);
			swayFrequencyMax =
				(float)number(operator, "frequencymax", swayFrequencyMax);
			swayPhaseMin = (float)number(operator, "phasemin", swayPhaseMin);
			swayPhaseMax = (float)number(operator, "phasemax", swayPhaseMax);
			swayScaleMin = (float)number(operator, "scalemin", swayScaleMin);
			swayScaleMax = (float)number(operator, "scalemax", swayScaleMax);

			// mask "1 0.5 0"：每一轴摆多少（0 表示那一轴不摆）
			float[] mask = numbers(operator, "mask", 1, 1);
			swayMaskX = mask[0];
			swayMaskY = mask[1];
		}

		maxCount = (int)number(root, "maxcount", maxCount);
		startTime = (float)number(root, "starttime", startTime);

		return new WeParticlePreset(rate, distanceMin, distanceMax,
			lifetimeMin, lifetimeMax, sizeMin, sizeMax, velocityMin[0],
			velocityMin[1], velocityMax[0], velocityMax[1], colorMin, colorMax,
			fadeInTime, swayFrequencyMin, swayFrequencyMax, swayPhaseMin,
			swayPhaseMax, swayScaleMin, swayScaleMax, swayMaskX, swayMaskY,
			maxCount, startTime);
	}

	/** 每秒最多发这么多（上限也拦一道，防止预设里速率离谱）。 */
	public float effectiveRate()
	{
		return Math.max(0, Math.min(rate, maxCount));
	}

	private static JsonObject object(JsonElement element)
	{
		return element != null && element.isJsonObject()
			? element.getAsJsonObject() : null;
	}

	private static JsonArray array(JsonObject parent, String key)
	{
		if(parent == null || !parent.has(key))
			return new JsonArray();

		JsonElement element = parent.get(key);
		return element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	private static String string(JsonObject parent, String key)
	{
		if(parent == null || !parent.has(key))
			return "";

		JsonElement element = parent.get(key);
		return element.isJsonPrimitive() ? element.getAsString() : "";
	}

	private static double number(JsonObject parent, String key, double fallback)
	{
		if(parent == null || !parent.has(key))
			return fallback;

		JsonElement element = parent.get(key);

		if(!element.isJsonPrimitive())
			return fallback;

		try
		{
			return element.getAsDouble();
		}catch(RuntimeException e)
		{
			return fallback;
		}
	}

	private static float[] numbers(JsonObject parent, String key,
		float... fallback)
	{
		if(parent == null || !parent.has(key))
			return fallback;

		return WeScene.numbers(parent.get(key), fallback);
	}
}
