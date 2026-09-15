/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.wurstclient.util.render.PostEffectQueue.Effect;

/**
 * 每个 {@code Effect} 都是「枚举值 + 一个 post chain json + 着色器里的一个 Mode
 * 分支」三件套，少一件都不会在编译期报错。
 *
 * <p>
 * 少 json 的后果尤其隐蔽：{@code PostEffectQueue} 把加载失败的效果记进
 * {@code failedEffects} 并<b>永久停用</b>（{@code PostEffectQueue.java:69}），
 * 所以线上表现是「这个选项永远是没效果的」，而不是崩或报错。少 Mode 分支同理：
 * 会掉进着色器最后那个 {@code else}，什么都不画。
 *
 * <p>
 * 这里就是把这三种「静默失败」变成编译后立刻能看见的失败。
 */
class PostEffectQueueEffectTest
{
	private static final String POST_PREFIX =
		"/assets/wurst/shaders/post/target_";
	private static final String SHADER_PATH =
		"/assets/wurst/shaders/program/target_effect.fsh";

	@Test
	void everyEffectHasAPostChainResource() throws Exception
	{
		for(Effect effect : Effect.values())
			assertNotNull(modeOf(effect), effect.name());
	}

	/**
	 * 两个效果共用同一个 Mode 是纯复制粘贴事故，而且症状只是「两个选项长得
	 * 一模一样」，不看代码很难发现。
	 */
	@Test
	void everyEffectUsesItsOwnShaderMode() throws Exception
	{
		Map<Integer, Effect> byMode = new HashMap<>();
		for(Effect effect : Effect.values())
		{
			int mode = modeOf(effect);
			Effect clash = byMode.put(mode, effect);
			assertNull(clash,
				"mode " + mode + " used by both " + clash + " and " + effect);
		}
	}

	@Test
	void theFragmentShaderHandlesEveryMode() throws Exception
	{
		String fsh = readResource(SHADER_PATH);
		for(Effect effect : Effect.values())
		{
			int mode = modeOf(effect);
			assertTrue(fsh.contains("Mode == " + mode), effect + " (mode "
				+ mode + ") has no branch in target_effect.fsh");
		}
	}

	/**
	 * post chain 里的 Mode 值和文件名必须对得上。{@code toString()} 是
	 * fileName 首字母大写，所以拿它反推文件名等于钉住了 {@code Effect}
	 * 自己的命名约定——post chain 路径就是由 fileName 拼出来的
	 * （{@code PostEffectQueue.java:114-115}）。
	 */
	@Test
	void thePostChainPathFollowsFromTheEnumName() throws Exception
	{
		assertEquals("outline", Effect.OUTLINE.toString().toLowerCase());
		assertEquals("bloom", Effect.BLOOM.toString().toLowerCase());
		assertEquals(4, modeOf(Effect.BLOOM));
	}

	private static int modeOf(Effect effect) throws Exception
	{
		JsonObject root = JsonParser
			.parseReader(new InputStreamReader(
				openPostChain(effect), StandardCharsets.UTF_8))
			.getAsJsonObject();

		JsonArray passes = root.getAsJsonArray("passes");
		assertNotNull(passes, effect + " has no passes");
		assertTrue(passes.size() >= 1, effect + " has no passes");

		JsonObject uniforms = passes.get(0).getAsJsonObject()
			.getAsJsonArray("uniforms").get(0).getAsJsonObject();
		assertEquals("Mode", uniforms.get("name").getAsString(),
			effect + " first uniform should be Mode");

		return uniforms.getAsJsonArray("values").get(0).getAsInt();
	}

	private static InputStream openPostChain(Effect effect)
	{
		String path = POST_PREFIX + effect.toString().toLowerCase(Locale.ROOT)
			+ ".json";
		InputStream in = PostEffectQueue.class.getResourceAsStream(path);
		assertNotNull(in, "missing post chain for " + effect + ": " + path);
		return in;
	}

	private static String readResource(String path) throws Exception
	{
		try(InputStream in = PostEffectQueue.class.getResourceAsStream(path))
		{
			assertNotNull(in, "missing resource: " + path);
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
