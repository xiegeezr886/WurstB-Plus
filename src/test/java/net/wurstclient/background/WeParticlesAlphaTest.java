/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 粒子的基础不透明度（预设的 {@code alpharandom}）。
 *
 * <p>
 * 用例里的 JSON 是**照抄 WE 安装目录里的 {@code fog1.json}** 删繁就简来的：
 * 那份预设写的是 {@code "alpharandom" : { min 0.15, max 0.2 }}，也就是每颗雾只有
 * 15%~20% 不透明度。我原来压根没读这个字段（{@code alpha()} 淡入满了一律返回 1），
 * 于是把雾画成实心、加性混合下亮约 6 倍 —— 那就是用户看到的光晕。这条用例把它钉住。
 * </p>
 */
final class WeParticlesAlphaTest
{
	private static final String FOG_LIKE = """
		{ "emitter" : [ { "name" : "sphererandom", "rate" : 60,
		  "distancemin" : 0, "distancemax" : 512 } ],
		  "initializer" : [
		    { "name" : "lifetimerandom", "min" : 3, "max" : 5 },
		    { "name" : "sizerandom", "min" : 1000, "max" : 2200 },
		    { "name" : "colorrandom", "min" : "255 255 255",
		      "max" : "255 255 255" },
		    { "name" : "alpharandom", "min" : 0.15, "max" : 0.2 } ],
		  "maxcount" : 20 }""";

	@Test
	void readsAlphaRandom()
	{
		WeParticlePreset preset = parse(FOG_LIKE);

		assertEquals(0.15F, preset.alphaMin(), 0.0001F);
		assertEquals(0.2F, preset.alphaMax(), 0.0001F);
	}

	/** 行为断言：真发出来的每一颗，不透明度都落在 0.15~0.2 内。 */
	@Test
	void particlesStayWithinThePresetOpacity()
	{
		WeParticles system = new WeParticles(parse(FOG_LIKE), 0x5EEDL, 1);
		system.advance(5F);

		assertTrue(system.count() > 0, "应当发出粒子");

		for(WeParticles.Particle particle : system.particles())
		{
			float alpha = particle.alpha();

			assertTrue(alpha <= 0.2F + 0.0001F,
				"不透明度超过了预设上限：" + alpha);
			assertTrue(alpha >= 0, "不透明度不能为负：" + alpha);
		}
	}

	/** 不写 alpharandom 时按不透明算（保持旧行为，别的场景不受影响）。 */
	@Test
	void defaultsToOpaqueWhenAbsent()
	{
		WeParticlePreset preset = parse(FOG_LIKE.replace(
			"{ \"name\" : \"alpharandom\", \"min\" : 0.15, \"max\" : 0.2 }",
			"{ \"name\" : \"rotationrandom\" }"));

		assertEquals(1F, preset.alphaMin(), 0.0001F);
		assertEquals(1F, preset.alphaMax(), 0.0001F);

		WeParticles system = new WeParticles(preset, 0x5EEDL, 1);
		system.advance(5F);

		assertTrue(system.count() > 0);

		for(WeParticles.Particle particle : system.particles())
			assertEquals(1F, particle.alpha(), 0.0001F);
	}

	private static WeParticlePreset parse(String json)
	{
		try
		{
			return WeParticlePreset.parse(json);

		}catch(java.io.IOException e)
		{
			throw new AssertionError("预设解析失败", e);
		}
	}
}
