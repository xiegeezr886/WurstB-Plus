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

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 文本对象的解析（{@link WeScene.TextLayer}）。
 *
 * <p>
 * 字段名照官方 {@code TextData} 与实测的例子来：{@code text} / {@code pointsize} /
 * {@code horizontalalign} / {@code verticalalign} / {@code color} / {@code alpha}，
 * 几何与图像层共用 origin / size / scale。
 * </p>
 */
final class WeSceneTextTest
{
	@Test
	void parsesATextObject() throws Exception
	{
		WeScene.TextLayer text = parse("""
			{"objects":[{"name":"Clock","text":"12:34",
			  "origin":"960 1035 0","size":"400 120","scale":"1 1",
			  "color":"1 0.5 0","alpha":0.8,"pointsize":96,
			  "horizontalalign":"left","verticalalign":"top",
			  "visible":true}]}
			""").texts().get(0);

		assertEquals("Clock", text.name());
		assertEquals("12:34", text.text());
		assertEquals(960, text.originX(), 0.01);
		assertEquals(1035, text.originY(), 0.01);
		assertEquals(400, text.sizeX(), 0.01);
		assertEquals(120, text.sizeY(), 0.01);
		assertEquals(1F, text.colorR(), 0.001);
		assertEquals(0.5F, text.colorG(), 0.001);
		assertEquals(0, text.colorB(), 0.001);
		assertEquals(0.8F, text.alpha(), 0.001);
		assertEquals(96, text.pointSize(), 0.001);
		assertEquals("left", text.horizontalAlign());
		assertEquals("top", text.verticalAlign());
	}

	/** 文本内容常常是脚本生成的（时钟就是这样），要能把 value 剥出来。 */
	@Test
	void unwrapsScriptBoundText() throws Exception
	{
		String wrapped = """
			{"objects":[{"name":"Clock",
			  "text":{"script":"export function update(v){return v;}",
			          "value":"07:45"},
			  "origin":"100 200 0","visible":true}]}
			""";

		assertEquals("07:45", parse(wrapped).texts().get(0).text());
	}

	/** 内容是空、取不出来、或图层不可见时都不产生文本层。 */
	@Test
	void skipsUnusableTextObjects() throws Exception
	{
		assertTrue(parse("""
			{"objects":[{"name":"空","text":"   ","origin":"1 2 0"}]}
			""").texts().isEmpty());

		assertTrue(parse("""
			{"objects":[{"name":"脚本取不出","text":{"script":"x"},
			  "origin":"1 2 0"}]}
			""").texts().isEmpty());

		assertTrue(parse("""
			{"objects":[{"name":"隐藏","text":"hi","visible":false,
			  "origin":"1 2 0"}]}
			""").texts().isEmpty());

		// 只是没有 text 字段的对象当然也不是文本层
		assertTrue(parse("""
			{"objects":[{"name":"不是文本","origin":"1 2 0"}]}
			""").texts().isEmpty());
	}

	/** 几何走父链：与图像层同一套 absoluteOrigin。 */
	@Test
	void inheritsGeometryFromParent() throws Exception
	{
		String scene = """
			{"objects":[
			  {"id":1,"name":"容器","origin":"1000 500 0","scale":"0.5 0.5 1",
			   "visible":true},
			  {"id":2,"parent":1,"name":"Clock","text":"00:00",
			   "origin":"200 -100 0","visible":true}
			]}
			""";

		WeScene.TextLayer text = parse(scene).texts().get(0);

		// 父链偏移按祖先缩放复合：1000 + 200*0.5 = 1100、500 + (-100)*0.5 = 450
		assertEquals(1100, text.originX(), 0.01);
		assertEquals(450, text.originY(), 0.01);
		// 缩放也继承
		assertEquals(0.5F, text.scaleX(), 0.001);
	}

	/** 没写 pointsize 时用兜底值（本项目自己定的，写清是猜的）。 */
	@Test
	void fallsBackToADefaultPointSize() throws Exception
	{
		WeScene.TextLayer text = parse("""
			{"objects":[{"name":"t","text":"x","origin":"1 2 0"}]}
			""").texts().get(0);

		assertEquals(48, text.pointSize(), 0.001);
	}

	// ------------------------------------------------------------------

	private static WeScene parse(String sceneJson) throws Exception
	{
		return WeScene.parse(sceneJson, Map.<String, String>of()::get);
	}
}
