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
 * 贴图旁边那份 {@code .tex-json} 里**声明**的帧序列 —— 帧格从元数据读，不从像素猜。
 *
 * <p>
 * <b>这是 WE 自己的做法。</b>{@code particle/fog/fog1} 与 {@code particle/fog/fog3}
 * 都是 8×8、每帧 128×128、共 64 帧，写在同一目录的 {@code fog1.tex-json} /
 * {@code fog3.tex-json} 里：</p>
 *
 * <pre>
 * { "clampuvs" : false, "format" : "r8", "nonpoweroftwo" : true,
 *   "spritesheetsequences" : [ { "duration" : 1, "frames" : 64,
 *                                "width" : 128, "height" : 128 } ] }
 * </pre>
 *
 * <p>
 * 单帧贴图是**同一个格式、只是没有这个键**（{@code halo.tex-json} 只有
 * {@code format}/{@code clampuvs}/{@code nomip}），所以"是不是图集"是一句可以读出来的
 * 事实，不需要任何阈值。</p>
 *
 * <p>
 * 渲染侧对应 WE 的 {@code assets/shaders/common_particles.h}：
 * {@code ComputeSpriteFrame()} 取 {@code numFrames = g_RenderVar1.z}、
 * {@code frameWidth = g_RenderVar1.x}、{@code frameHeight = g_RenderVar1.y}，
 * 帧号是 {@code floor(lifetime * numFrames)}，行列按
 * {@code u = frac(frame * frameWidth)} / {@code v = floor(frame * frameWidth) * frameHeight}
 * —— 即**行优先**排布，一行的帧数就是 {@code 1 / frameWidth}（= 贴图宽 ÷ 帧宽）。</p>
 *
 * <p>
 * <b>之前那几版为什么全错</b>：我在像素上找周期（逐像素残差、投影周期、连通块计数、
 * 带容差的模板匹配），真贴图一次次把它们否掉 —— {@code fog3} 的帧边界上根本没有留白，
 * 逐像素与投影都量不出周期。现在这条路是 0 猜测的：读文件里写着的数字。
 * 拿不到 {@code .tex-json} 时才退回单帧，而不是"猜一个看起来合理的格子"。</p>
 */
public record WeTextureSheet(int frames, int frameWidth, int frameHeight,
	int columns, int rows, float duration)
{
	/** 没有帧序列：整张贴图就是一帧。 */
	public static final WeTextureSheet SINGLE =
		new WeTextureSheet(1, 0, 0, 1, 1, 0);

	/**
	 * 解析一份 {@code .tex-json}。
	 *
	 * @param texJson
	 *            {@code .tex-json} 的内容；{@code null}/空 按单帧算
	 * @param imageWidth
	 *            贴图实际像素宽，用来把「帧宽」换算成列数
	 * @param imageHeight
	 *            贴图实际像素高
	 * @throws IOException
	 *             元数据自相矛盾时抛（帧数超过格子数、帧宽不整除贴图宽……）——
	 *             宁可退回单帧并报出来，也不要按一个错的格子去采样
	 */
	public static WeTextureSheet parse(String texJson, int imageWidth,
		int imageHeight) throws IOException
	{
		if(texJson == null || texJson.isBlank())
			return SINGLE;

		JsonObject root;

		try
		{
			root = JsonUtils.GSON.fromJson(texJson, JsonObject.class);

		}catch(RuntimeException e)
		{
			throw new IOException("tex-json 不是合法 JSON：" + e.getMessage());
		}

		if(root == null || !root.has("spritesheetsequences"))
			return SINGLE;

		JsonElement sequences = root.get("spritesheetsequences");

		if(!sequences.isJsonArray() || sequences.getAsJsonArray().isEmpty())
			return SINGLE;

		if(imageWidth <= 0 || imageHeight <= 0)
			throw new IOException(
				"贴图尺寸不合理：" + imageWidth + "x" + imageHeight);

		// WE 的图集允许一条贴图里放多段序列（不同动画共用一张图），粒子只取第一段。
		JsonArray array = sequences.getAsJsonArray();
		JsonElement first = array.get(0);

		if(!first.isJsonObject())
			throw new IOException("spritesheetsequences[0] 不是对象");

		JsonObject sequence = first.getAsJsonObject();
		int frames = (int)number(sequence, "frames", 0);
		int frameWidth = (int)number(sequence, "width", 0);
		int frameHeight = (int)number(sequence, "height", 0);
		float duration = (float)number(sequence, "duration", 0);

		if(frames < 1)
			throw new IOException("帧数不合理：" + frames);

		if(frameWidth < 1 || frameHeight < 1)
			throw new IOException(
				"帧尺寸不合理：" + frameWidth + "x" + frameHeight);

		if(imageWidth % frameWidth != 0 || imageHeight % frameHeight != 0)
			throw new IOException("帧尺寸除不尽贴图：" + imageWidth + "x"
				+ imageHeight + " 装不下 " + frameWidth + "x" + frameHeight);

		int columns = imageWidth / frameWidth;
		int rows = imageHeight / frameHeight;

		if(frames > columns * rows)
			throw new IOException("帧数 " + frames + " 超过格子数 " + columns + "x"
				+ rows + "（行优先排布放不下）");

		return new WeTextureSheet(frames, frameWidth, frameHeight, columns, rows,
			duration);
	}

	private static double number(JsonObject parent, String key, double fallback)
	{
		if(parent == null || !parent.has(key))
			return fallback;

		JsonElement element = parent.get(key);

		if(!element.isJsonPrimitive()
			|| !element.getAsJsonPrimitive().isNumber())
			return fallback;

		return element.getAsDouble();
	}

	public boolean isSheet()
	{
		return frames > 1;
	}
}
