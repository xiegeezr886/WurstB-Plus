/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 内置工具模型（{@code models/util/*}）：Wallpaper Engine 提供、**不随包发布**。
 *
 * <p>
 * 实测「流萤」里 {@code Background}／{@code Progress Bar}／{@code Settings Container}
 * ／{@code Audio Bars}／{@code 纯色} 等 10 个对象都指向 {@code util/solidlayer.json}，
 * 以前因为"模型文件读不到"被整个丢掉 —— 画面上就少了一块。这里钉住三种情形：
 * solidlayer 要变成"贴图名为空"的纯色层、composelayer 要丢弃、普通模型不受影响。
 * </p>
 */
final class WeSceneSolidLayerTest
{
	private static final Map<String, String> FILES = Map.of(
		"models/util/solidlayer.json", "{}",
		"models/util/composelayer.json", "{}",
		"models/normal.json", "{\"material\":\"materials/normal.json\"}",
		"materials/normal.json",
		"{\"passes\":[{\"textures\":[\"textures/albedo.png\"]}]}");

	@Test
	void turnsSolidLayersIntoTexturelessLayers() throws Exception
	{
		List<WeScene.Layer> layers = parse("""
			{"objects":[
			  {"name":"Background","image":"models/util/solidlayer.json",
			   "origin":"100 200","size":"300 150","scale":"1 1",
			   "color":"0.2 0.4 0.6","alpha":0.5,"visible":true}
			]}
			""");

		assertEquals(1, layers.size());
		WeScene.Layer layer = layers.get(0);

		assertEquals("Background", layer.name());
		assertEquals("", layer.texture(), "纯色层没有贴图，用空贴图名标记");
		assertEquals(100, layer.originX(), 0.01);
		assertEquals(200, layer.originY(), 0.01);
		assertEquals(300, layer.sizeX(), 0.01);
		assertEquals(150, layer.sizeY(), 0.01);
		assertEquals(0.2F, layer.colorR(), 0.001);
		assertEquals(0.4F, layer.colorG(), 0.001);
		assertEquals(0.6F, layer.colorB(), 0.001);
		assertEquals(0.5F, layer.alpha(), 0.001);
	}

	/** 没写 color 的纯色层用白色 —— 渲染那边会拿它去着色，正好得到图层要的颜色。 */
	@Test
	void defaultsSolidLayersToWhite() throws Exception
	{
		List<WeScene.Layer> layers = parse("""
			{"objects":[
			  {"name":"纯色","image":"models/util/solidlayer.json",
			   "size":"10 10","visible":true}
			]}
			""");

		assertEquals(1, layers.size());
		assertEquals(1F, layers.get(0).colorR(), 0.001);
		assertEquals(1F, layers.get(0).colorG(), 0.001);
		assertEquals(1F, layers.get(0).colorB(), 0.001);
	}

	/** 分组/合成层自身不产生像素，丢弃（它的子对象才是内容）。 */
	@Test
	void dropsCompositionLayers() throws Exception
	{
		List<WeScene.Layer> layers = parse("""
			{"objects":[
			  {"name":"模糊","image":"models/util/composelayer.json",
			   "size":"100 100","visible":true}
			]}
			""");

		assertTrue(layers.isEmpty(), "分组层不该变成图层");
	}

	/** 普通模型照旧走 模型 → 材质 → 贴图 那条链，不能被新分支抢走。 */
	@Test
	void leavesNormalModelsAlone() throws Exception
	{
		List<WeScene.Layer> layers = parse("""
			{"objects":[
			  {"name":"人物","image":"models/normal.json",
			   "size":"100 100","visible":true}
			]}
			""");

		assertEquals(1, layers.size());
		assertEquals("textures/albedo.png", layers.get(0).texture());
	}

	/** solidlayer 也带 effects，抠像参数要照样解析出来。 */
	@Test
	void stillReadsColorKeyOnSolidLayers() throws Exception
	{
		List<WeScene.Layer> layers = parse("""
			{"objects":[
			  {"name":"纯色","image":"models/util/solidlayer.json",
			   "size":"10 10","visible":true,
			   "effects":[{"file":"effects/colorkey/effect.json",
			     "passes":[{"constantshadervalues":{
			       "color":"0 0 1","tolerance":0.5}}]}]}
			]}
			""");

		assertEquals(1, layers.size());
		assertNotNull(layers.get(0).colorKey());
		assertEquals(1F, layers.get(0).colorKey().blue(), 0.001);
	}

	// ------------------------------------------------------------------

	private static List<WeScene.Layer> parse(String sceneJson)
		throws Exception
	{
		return WeScene.parse(sceneJson, FILES::get).layers();
	}
}
