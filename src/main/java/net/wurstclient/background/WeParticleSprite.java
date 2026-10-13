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

import net.wurstclient.util.json.JsonUtils;

/**
 * 粒子预设引用的那份材质（{@code passes[].blending} 与 {@code textures[0]}）。
 *
 * <p>
 * <b>为什么需要它</b>：粒子预设自己**不写混合模式**，混合模式在它引用的材质里。
 * 预设顶层有个 {@code material} 字段（实测 {@code particles/presets/fog1.json} →
 * {@code materials/presets/fog1.json}），那份材质长这样：</p>
 *
 * <pre>
 * { "passes" : [ { "blending" : "additive", "shader" : "genericparticle",
 *                  "textures" : [ "particle/fog/fog1" ] } ] }
 * </pre>
 *
 * <p>
 * 两个字段各治一个毛病：{@code blending} 决定加性还是普通混合；
 * {@code textures[0]} 是**真正的粒子贴图** —— 我原先一律画自己合成的白色柔光点，
 * 而雾用的是 {@code particle/fog/fog1} 这种很淡的云，白点铺到 1000~2200 画布单位
 * 就是满屏白光。贴图从 WE 安装目录的 {@code assets/materials/<名字>.tex} 取。</p>
 *
 * <p>
 * 缺 {@code textures} 时返回 {@code null}（宁可退回白点，也别拿个空贴图去画）；
 * 缺 {@code blending} 时按**普通混合**算 —— 加性只会更亮，默认不亮是安全的那一侧。</p>
 */
public record WeParticleSprite(String texture, boolean additive)
{
	private static final String ADDITIVE = "additive";

	public static WeParticleSprite parse(String materialJson)
	{
		if(materialJson == null || materialJson.isBlank())
			return null;

		JsonObject root;

		try
		{
			root = JsonUtils.GSON.fromJson(materialJson, JsonObject.class);
		}catch(RuntimeException e)
		{
			return null;
		}

		if(root == null || !root.has("passes")
			|| !root.get("passes").isJsonArray())
			return null;

		JsonArray passes = root.getAsJsonArray("passes");

		for(JsonElement element : passes)
		{
			if(!element.isJsonObject())
				continue;

			JsonObject pass = element.getAsJsonObject();
			String texture = firstTexture(pass);

			if(texture != null)
				return new WeParticleSprite(texture,
					ADDITIVE.equalsIgnoreCase(string(pass, "blending")));
		}

		return null;
	}

	private static String firstTexture(JsonObject pass)
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

	private static String string(JsonObject parent, String key)
	{
		if(parent == null || !parent.has(key))
			return "";

		JsonElement element = parent.get(key);
		return element.isJsonPrimitive() ? element.getAsString() : "";
	}
}
