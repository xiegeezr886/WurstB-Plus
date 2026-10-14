/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * 字体引用的取舍（{@link WeSceneFont#canLoad(String)} 与缓存键）。
 *
 * <p>
 * 判据只有一条，但它决定"能不能用作者的字体"：MC 的字形加载是 STB TrueType，
 * 只吃 {@code glyf} 轮廓的 {@code .ttf}；{@code .otf} 是 CFF 轮廓，STB 的
 * {@code stbtt_InitFont} 直接返回 0。{@code systemfont_*} 指的是用户系统里的字体，
 * 作品包里不会有。三类引用在场景里都真实存在（实测 9 个对象 {@code .ttf}、
 * 7 个 {@code systemfont_arial}、1 个 {@code .otf}）。
 * </p>
 */
final class WeSceneFontRulesTest
{
	@Test
	void acceptsThePackagedTrueTypeFont()
	{
		assertTrue(
			WeSceneFont.canLoad("fonts/workshop/3759258840/yuanshen.ttf"));
		assertTrue(WeSceneFont.canLoad("fonts/MyFont.TTF"));
	}

	@Test
	void refusesOtfAndSystemFonts()
	{
		assertFalse(WeSceneFont.canLoad("fonts/Atami-Regular.otf"));
		assertFalse(WeSceneFont.canLoad("systemfont_arial"));
		assertFalse(WeSceneFont.canLoad("systemfont_segoeui"));
		assertFalse(WeSceneFont.canLoad(null));
		assertFalse(WeSceneFont.canLoad(""));
		assertFalse(WeSceneFont.canLoad("   "));
	}

	/** 缓存键把字号并进去：同一个字体在不同字号下各要一份 provider。 */
	@Test
	void fontKeyIncludesTheRoundedPointSize()
	{
		assertEquals("fonts/a.ttf@40", WeSceneWallpaper.fontKey("fonts/a.ttf", 40));
		assertEquals("fonts/a.ttf@40", WeSceneWallpaper.fontKey(" fonts/a.ttf ", 40.4F));
		assertEquals("fonts/a.ttf@41", WeSceneWallpaper.fontKey("fonts/a.ttf", 40.6F));
		assertEquals("", WeSceneWallpaper.fontKey(null, 40));
	}

	/**
	 * 交给 STB 之前的格式闸门。
	 *
	 * <p>
	 * 这一条是用一次 JVM 崩溃换来的：把 18 字节垃圾喂给 {@code stbtt_InitFont}，
	 * JVM 直接崩在 {@code lwjgl_stb.dll}（{@code EXCEPTION_ACCESS_VIOLATION}），
	 * Java 侧 catch 不到。所以先在 Java 里看 sfnt 标记，脏数据根本不进原生库。
	 * </p>
	 */
	@Test
	void requireTrueTypeAcceptsSfntAndRefusesEverythingElse()
	{
		// 四种合法标记
		assertDoesNotThrow(() -> WeSceneFont.requireTrueType(sfnt(0x00010000)));
		assertDoesNotThrow(() -> WeSceneFont.requireTrueType(sfnt(0x74727565)));
		assertDoesNotThrow(() -> WeSceneFont.requireTrueType(sfnt(0x74746366)));

		// OTTO = CFF 轮廓（.otf）：报错要说清是 CFF，而不是含混的"打不开"
		IOException otto = assertThrows(IOException.class,
			() -> WeSceneFont.requireTrueType(sfnt(0x4F54544F)));
		assertTrue(otto.getMessage().contains("CFF"), otto.getMessage());

		// 垃圾、截断、null
		assertThrows(IOException.class, () -> WeSceneFont
			.requireTrueType("this is not a font".getBytes(StandardCharsets.US_ASCII)));
		assertThrows(IOException.class,
			() -> WeSceneFont.requireTrueType(new byte[]{0, 1, 0}));
		assertThrows(IOException.class,
			() -> WeSceneFont.requireTrueType(null));
	}

	/** 12 字节的假 sfnt 头（4 字节标记 + 8 字节填充）。 */
	private static byte[] sfnt(int tag)
	{
		return new byte[]{(byte)(tag >>> 24), (byte)(tag >>> 16),
			(byte)(tag >>> 8), (byte)tag, 0, 0, 0, 0, 0, 0, 0, 0};
	}
}
