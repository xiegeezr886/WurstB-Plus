/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 粒子材质（{@link WeParticleSprite}）。
 *
 * <p>
 * 用例里的三段 JSON 是**照抄 WE 安装目录里真文件**的（
 * {@code assets/presets/fog/materials/presets/fog1.json} 等），不是编的：
 * 实测雾与火花材质都写 {@code "blending": "additive"}，贴图分别是
 * {@code particle/fog/fog1} 与 {@code particle/light/flare_1}。
 * </p>
 */
final class WeParticleSpriteTest
{
	private static final String FOG = """
		{ "passes" : [ { "blending" : "additive", "cullmode" : "nocull",
		  "depthtest" : "disabled", "depthwrite" : "disabled",
		  "shader" : "genericparticle",
		  "textures" : [ "particle/fog/fog1" ] } ] }""";

	@Test
	void readsTextureAndAdditive()
	{
		WeParticleSprite sprite = WeParticleSprite.parse(FOG);

		assertNotNull(sprite);
		assertEquals("particle/fog/fog1", sprite.texture());
		assertTrue(sprite.additive());
	}

	/** 不写 blending 时按**普通混合**算：加性只会更亮，默认不亮是安全的一侧。 */
	@Test
	void defaultsToNormalBlending()
	{
		WeParticleSprite sprite = WeParticleSprite.parse("""
			{ "passes" : [ { "shader" : "genericparticle",
			  "textures" : [ "particle/fog/fog3" ] } ] }""");

		assertNotNull(sprite);
		assertEquals("particle/fog/fog3", sprite.texture());
		assertFalse(sprite.additive());
	}

	/** 大小写不敏感：官方文件里是手写的字符串。 */
	@Test
	void blendingIsCaseInsensitive()
	{
		WeParticleSprite sprite = WeParticleSprite.parse("""
			{ "passes" : [ { "blending" : "Additive",
			  "textures" : [ "particle/light/flare_1" ] } ] }""");

		assertNotNull(sprite);
		assertTrue(sprite.additive());
	}

	/** 没有贴图的材质不认 —— 宁可退回白点，也别拿个空贴图去画。 */
	@Test
	void rejectsMaterialWithoutTexture()
	{
		assertNull(WeParticleSprite.parse("""
			{ "passes" : [ { "blending" : "additive" } ] }"""));
		assertNull(WeParticleSprite.parse("""
			{ "passes" : [ { "blending" : "additive", "textures" : [] } ] }"""));
	}

	/** 前面那些 pass 没有贴图时，取第一个**有**贴图的 pass 的混合模式。 */
	@Test
	void skipsPassesWithoutTexture()
	{
		WeParticleSprite sprite = WeParticleSprite.parse("""
			{ "passes" : [ { "blending" : "additive" },
			  { "blending" : "translucent",
			    "textures" : [ "particle/fog/fog1" ] } ] }""");

		assertNotNull(sprite);
		assertEquals("particle/fog/fog1", sprite.texture());
		assertFalse(sprite.additive());
	}

	@Test
	void rejectsGarbage()
	{
		assertNull(WeParticleSprite.parse(null));
		assertNull(WeParticleSprite.parse(""));
		assertNull(WeParticleSprite.parse("不是 JSON"));
		assertNull(WeParticleSprite.parse("{}"));
		assertNull(WeParticleSprite.parse("{ \"passes\" : 3 }"));
	}
}
