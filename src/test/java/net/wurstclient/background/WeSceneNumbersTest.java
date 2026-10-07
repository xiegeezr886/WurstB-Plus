/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

/**
 * {@link WeScene#numbers(com.google.gson.JsonElement, float...)}：向量值的各种形态。
 *
 * <p>
 * 实测「流萤」里大量 {@code origin}/{@code scale} 是
 * {@code {"script":…,"scriptproperties":…,"value":"x y z"}} —— 绑定到脚本/用户属性
 * 的形式。以前遇到对象就退回兜底值，而兜底是画布中心，图层会跳到画面正中（用户反馈的
 * "位移"的一条通路）。这里钉住：值对象要取里面的 {@code value}。
 * </p>
 */
final class WeSceneNumbersTest
{
	@Test
	void readsPlainVectors()
	{
		assertArrayEquals(new float[]{1, 2, 3},
			WeScene.numbers(JsonParser.parseString("\"1 2 3\""), 0, 0, 0),
			0.001F);
	}

	/** 绑定脚本的值对象：真实数值在 value 里。 */
	@Test
	void readsValuesWrappedInScriptObjects()
	{
		String wrapped = """
			{"script":"export function update(v){return v;}",
			 "scriptproperties":{"isMovable":true},
			 "value":"178.12402 2039.78345 0.00000"}
			""";

		assertArrayEquals(new float[]{178.12402F, 2039.78345F, 0},
			WeScene.numbers(JsonParser.parseString(wrapped), 0, 0, 0),
			0.001F);
	}

	/** 值对象里套值对象也要能剥到底。 */
	@Test
	void unwrapsNestedValueObjects()
	{
		String nested = "{\"value\":{\"value\":\"5 6 7\"}}";

		assertArrayEquals(new float[]{5, 6, 7},
			WeScene.numbers(JsonParser.parseString(nested), 0, 0, 0), 0.001F);
	}

	/** 缺项、空值、类型不对时仍然用兜底值，且不该抛异常。 */
	@Test
	void fallsBackWhenThereIsNothingUsable()
	{
		assertArrayEquals(new float[]{9, 9, 9},
			WeScene.numbers(null, 9, 9, 9), 0.001F);
		assertArrayEquals(new float[]{9, 9, 9},
			WeScene.numbers(JsonParser.parseString("\"\""), 9, 9, 9), 0.001F);
		assertArrayEquals(new float[]{9, 9, 9},
			WeScene.numbers(JsonParser.parseString("{}"), 9, 9, 9), 0.001F);
		assertArrayEquals(new float[]{9, 9, 9},
			WeScene.numbers(JsonParser.parseString("[1,2,3]"), 9, 9, 9),
			0.001F);
	}

	/** 只写了前两个分量时，第三个用兜底值补。 */
	@Test
	void padsMissingComponents()
	{
		assertArrayEquals(new float[]{4, 5, 9},
			WeScene.numbers(JsonParser.parseString("\"4 5\""), 0, 0, 9),
			0.001F);
	}
}
