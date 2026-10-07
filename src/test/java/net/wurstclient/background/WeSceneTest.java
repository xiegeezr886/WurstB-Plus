/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

/**
 * Tests the scene layer tree reader.
 *
 * <p>
 * The fixture mirrors the shape of the real Persica {@code scene.json}: a
 * separator object that references a model the package does not contain, a
 * particle and a text object that carry no image at all, one image layer whose
 * {@code visible} is a user property, and one that is switched off.
 */
final class WeSceneTest
{
	private static final float EPSILON = 0.0001F;

	private static final String SCENE = """
		{
			"camera": {"eye": "-213.64711 -20.90339 0.00000"},
			"general": {
				"cameraparallax": true,
				"cameraparallaxamount": 0.5,
				"orthogonalprojection": {"height": 2160, "width": 3840},
				"zoom": 1.0
			},
			"objects": [
				{"name": "----", "image": "models/util/fullscreenlayer.json",
				 "visible": true, "alpha": 1.0},
				{"name": "Blossom backdrop",
				 "image": "models/Blossom backdrop.json",
				 "origin": "1920.00000 1080.00000 0.00000",
				 "size": "4000.00000 2250.00000",
				 "scale": "1.00000 1.00000 1.00000",
				 "color": "1.00000 1.00000 1.00000",
				 "parallaxDepth": "0.05000 0.05000",
				 "visible": true},
				{"name": "Snow flat", "particle": "particles/presets/snowflat.json",
				 "origin": "1920.00000 1080.00000 0.00000",
				 "parallaxDepth": "0.07000 0.07000",
				 "visible": {"user": "snow", "value": true}},
				{"name": "Clock", "origin": "960.00000 1035.00000 0.00000",
				 "size": "626.00000 366.00000",
				 "visible": {"user": "clock", "value": false}},
				{"name": "Branch", "image": "models/Blossom 2.0.json",
				 "origin": "1920.00000 1080.00000 0.00000",
				 "size": "4000.00000 2250.00000",
				 "scale": "1.03000 1.03000 1.03000",
				 "alpha": 0.75, "color": "0.5 0.25 1.0",
				 "parallaxDepth": "0.09000 0.09000", "visible": true}
			]
		}
		""";

	private static Map<String, String> files()
	{
		Map<String, String> files = new HashMap<>();
		files.put("models/Blossom backdrop.json",
			"{\"autosize\": true, \"material\": \"materials/Blossom backdrop.json\"}");
		files.put("materials/Blossom backdrop.json",
			"{\"passes\": [{\"shader\": \"genericimage2\", "
				+ "\"textures\": [\"Blossom backdrop v2\"]}]}");
		files.put("models/Blossom 2.0.json",
			"{\"autosize\": true, \"material\": \"materials/Blossom 2.0.json\"}");
		files.put("materials/Blossom 2.0.json",
			"{\"passes\": [{\"textures\": [\"Blossom 2.0\"]}]}");
		return files;
	}

	private static WeScene parse() throws IOException
	{
		return WeScene.parse(SCENE, files()::get);
	}

	@Test
	void itReadsTheCanvasAndParallaxSettings() throws IOException
	{
		WeScene scene = parse();

		assertEquals(3840, scene.width());
		assertEquals(2160, scene.height());
		assertEquals(1.0F, scene.zoom(), EPSILON);
		assertTrue(scene.parallax());
		assertEquals(0.5F, scene.parallaxAmount(), EPSILON);
	}

	/** 只有真的能画出东西的对象才成为图层，顺序保持不变。 */
	@Test
	void itKeepsOnlyDrawableLayers() throws IOException
	{
		WeScene scene = parse();

		assertEquals(2, scene.layers().size());
		assertEquals("Blossom backdrop", scene.layers().get(0).name());
		assertEquals("Branch", scene.layers().get(1).name());
	}

	/**
	 * 粒子层单独收，并记住它插在第几个图像图层之前——Persica 的两层雪分处树枝
	 * 前后，顺序丢了近处那层就不会盖住树枝。
	 */
	@Test
	void itKeepsParticleLayersInSceneOrder() throws IOException
	{
		WeScene scene = parse();

		assertEquals(1, scene.particles().size());

		WeScene.ParticleLayer snow = scene.particles().get(0);
		assertEquals("Snow flat", snow.name());
		assertEquals("particles/presets/snowflat.json", snow.preset());
		// 夹具里它在 Blossom backdrop 之后，所以插在第 1 个图层之前
		assertEquals(1, snow.layerIndex());
		assertEquals(0.07F, snow.parallaxX(), EPSILON);
	}

	@Test
	void itReadsLayerTransforms() throws IOException
	{
		WeScene.Layer branch = parse().layers().get(1);

		assertEquals("Blossom 2.0", branch.texture());
		assertEquals(1920, branch.originX(), EPSILON);
		assertEquals(1080, branch.originY(), EPSILON);
		assertEquals(4000, branch.sizeX(), EPSILON);
		assertEquals(2250, branch.sizeY(), EPSILON);
		assertEquals(1.03F, branch.scaleX(), EPSILON);
		assertEquals(0.75F, branch.alpha(), EPSILON);
		assertEquals(0.5F, branch.colorR(), EPSILON);
		assertEquals(1.0F, branch.colorB(), EPSILON);
		assertEquals(0.09F, branch.parallaxX(), EPSILON);
	}

	/**
	 * 缺字段走默认值：缩放是 1、**原点缺省是 (0,0) 而不是画布中心**。
	 *
	 * <p>
	 * 这条以前断言的是画布中心，那是我的错误假设。参考实现的
	 * {@code ObjectParser.cpp} 里写得很清楚：
	 * {@code .origin = it.user("origin", project.properties, glm::vec3(0.0f))}
	 * —— 缺省就是零，该对象在 Wallpaper Engine 里落在画布左上角。
	 * </p>
	 */
	@Test
	void missingFieldsFallBack() throws IOException
	{
		String minimal = """
			{"general": {"orthogonalprojection": {"width": 3840, "height": 2160}},
			 "objects": [{"name": "Only", "image": "models/m.json"}]}
			""";

		Map<String, String> files = new HashMap<>();
		files.put("models/m.json", "{\"material\": \"materials/m.json\"}");
		files.put("materials/m.json", "{\"passes\": [{\"textures\": [\"t\"]}]}");

		WeScene.Layer layer =
			WeScene.parse(minimal, files::get).layers().get(0);

		assertEquals(1, layer.scaleX(), EPSILON);
		assertEquals(1, layer.alpha(), EPSILON);
		assertEquals(0, layer.originX(), EPSILON);
		assertEquals(0, layer.originY(), EPSILON);
		// autosize：没写尺寸就交给贴图自己的大小
		assertEquals(0, layer.sizeX(), EPSILON);
	}

	@Test
	void itRefusesUnreadableScenes()
	{
		assertThrows(IOException.class,
			() -> WeScene.parse(null, name -> null));
		assertThrows(IOException.class,
			() -> WeScene.parse("not json", name -> null));

		// 画布尺寸不合理
		assertThrows(IOException.class, () -> WeScene.parse(
			"{\"general\": {\"orthogonalprojection\": {\"width\": 0}}",
			name -> null));
	}

	/** 一个图层都解析不出来不是错误，交给载入方去判断要不要放弃。 */
	@Test
	void anEmptySceneParsesButHasNoLayers() throws IOException
	{
		assertEquals(0, WeScene.parse("{}", name -> null).layers().size());
	}

	@Test
	void itResolvesTextureEntryNames()
	{
		List<String> entries = List.of("scene.json",
			"materials/Blossom 2.0.tex", "materials/masks/x.tex");

		assertEquals("materials/Blossom 2.0.tex",
			WeScene.textureEntryName("Blossom 2.0", entries));

		// 大小写不敏感，也接受已经带子目录的写法
		assertEquals("materials/Blossom 2.0.tex",
			WeScene.textureEntryName("blossom 2.0", entries));
		assertEquals("materials/masks/x.tex",
			WeScene.textureEntryName("masks/x", entries));

		assertNull(WeScene.textureEntryName("nope", entries));
		assertNull(WeScene.textureEntryName(null, entries));
		assertNull(WeScene.textureEntryName("Blossom 2.0", null));
	}

	@Test
	void itParsesVectors()
	{
		assertEquals(0.05F, WeScene
			.numbers(JsonParser.parseString("\"0.05 0.07\""), 0, 0, 0)[0],
			EPSILON);

		// 缺的分量保留兜底值
		float[] one = WeScene.numbers(JsonParser.parseString("\"7\""), 1, 2, 3);
		assertEquals(7, one[0], EPSILON);
		assertEquals(2, one[1], EPSILON);
		assertEquals(3, one[2], EPSILON);

		// 解析不了的部分也一样
		float[] broken =
			WeScene.numbers(JsonParser.parseString("\"x y\""), 1, 2, 3);
		assertEquals(1, broken[0], EPSILON);
		assertEquals(2, broken[1], EPSILON);

		assertEquals(4, WeScene.numbers(null, 4, 5)[0], EPSILON);
		assertEquals(0, WeScene.numbers(JsonParser.parseString("null"), 0, 0)[0],
			EPSILON);
	}
}
