/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.Test;

/**
 * {@code .tex-json} 的解析（{@link WeTextureSheet}）—— 帧格的**唯一**来路。
 *
 * <p>
 * 下面三段 JSON 是 WE 安装目录里那三份文件的原文
 * （{@code assets/materials/particle/fog/fog1.tex-json}、{@code …/fog3.tex-json}、
 * {@code …/halo_6.tex-json}），逐字抄进来当固定样本：雾两份声明 64 帧、每帧 128×128，
 * 1024×1024 的贴图因此是 8×8；{@code halo_6} 那份**没有 {@code spritesheetsequences}
 * 键**，所以是单帧。真贴图在用户机器上，不能当测试依赖，但这两类"声明形态"可以。
 * </p>
 */
final class WeTextureSheetTest
{
	/** fog1.tex-json 原文。 */
	private static final String FOG1 = """
		{
			"clampuvs" : false,
			"format" : "r8",
			"nonpoweroftwo" : true,
			"spritesheetsequences" :
			[
				{
					"duration" : 1,
					"frames" : 64,
					"height" : 128,
					"width" : 128
				}
			]
		}
		""";

	/** fog3.tex-json 原文。 */
	private static final String FOG3 = """
		{
			"clampuvs" : false,
			"format" : "rg88",
			"nomip" : false,
			"nonpoweroftwo" : true,
			"spritesheetsequences" :
			[
				{
					"duration" : 1,
					"frames" : 64,
					"height" : 128,
					"width" : 128
				}
			]
		}
		""";

	/** halo_6.tex-json 原文：没有 spritesheetsequences，就是单帧。 */
	private static final String HALO_6 = """
		{
			"format" : "rgba8888",
			"clampuvs" : true,
			"nomip" : true
		}
		""";

	/** 1024×1024 的雾贴图 → 8 列 8 行 64 帧。 */
	@Test
	void readsBothFogSheetsAsEightByEight() throws IOException
	{
		for(String json : new String[]{FOG1, FOG3})
		{
			WeTextureSheet sheet = WeTextureSheet.parse(json, 1024, 1024);

			assertTrue(sheet.isSheet());
			assertEquals(64, sheet.frames());
			assertEquals(128, sheet.frameWidth());
			assertEquals(128, sheet.frameHeight());
			assertEquals(8, sheet.columns());
			assertEquals(8, sheet.rows());
			assertEquals(1F, sheet.duration());
		}
	}

	/** halo_6：没有帧序列 → 单帧，且不抛异常。 */
	@Test
	void treatsMissingSequenceAsSingleFrame() throws IOException
	{
		WeTextureSheet sheet = WeTextureSheet.parse(HALO_6, 128, 128);

		assertFalse(sheet.isSheet());
		assertEquals(1, sheet.frames());
		assertEquals(1, sheet.columns());
		assertEquals(1, sheet.rows());
	}

	/** 没有 tex-json（null）、空文件、空序列都按单帧算。 */
	@Test
	void treatsAbsentMetadataAsSingleFrame() throws IOException
	{
		assertFalse(WeTextureSheet.parse(null, 64, 64).isSheet());
		assertFalse(WeTextureSheet.parse("", 64, 64).isSheet());
		assertFalse(
			WeTextureSheet.parse("{\"spritesheetsequences\":[]}", 64, 64)
				.isSheet());
	}

	/**
	 * 帧数不必填满格子：WE 自带图里有 {@code frames:3}（每帧 128×128）这种。
	 */
	@Test
	void allowsAFrameCountSmallerThanTheGrid() throws IOException
	{
		String json = """
			{ "spritesheetsequences" :
				[ { "duration" : 1, "frames" : 3, "width" : 128, "height" : 128 } ] }
			""";
		WeTextureSheet sheet = WeTextureSheet.parse(json, 1024, 1024);

		assertEquals(3, sheet.frames());
		assertEquals(8, sheet.columns());
		assertEquals(8, sheet.rows());
	}

	/** 非正方形的帧格也能表达（先前把 fog3 看成 16×8 就是缺了这条路）。 */
	@Test
	void supportsNonSquareFrameGrids() throws IOException
	{
		String json = """
			{ "spritesheetsequences" :
				[ { "frames" : 128, "width" : 64, "height" : 128 } ] }
			""";
		WeTextureSheet sheet = WeTextureSheet.parse(json, 1024, 1024);

		assertEquals(16, sheet.columns());
		assertEquals(8, sheet.rows());
		assertEquals(128, sheet.frames());
	}

	/** 自相矛盾的声明一律报错，由调用方退回单帧 —— 不按错的格子采样。 */
	@Test
	void rejectsInconsistentMetadata()
	{
		assertThrows(IOException.class,
			() -> WeTextureSheet.parse(FOG1, 512, 512));

		assertThrows(IOException.class, () -> WeTextureSheet.parse("""
			{ "spritesheetsequences" :
				[ { "frames" : 65, "width" : 128, "height" : 128 } ] }
			""", 1024, 1024));

		assertThrows(IOException.class, () -> WeTextureSheet.parse(
			"{ \"spritesheetsequences\" : [ { \"frames\" : 0 } ] }", 1024,
			1024));

		assertThrows(IOException.class, () -> WeTextureSheet.parse(
			"{ \"spritesheetsequences\" : [ { \"frames\" : 4 } ] }", 1024,
			1024));

		assertThrows(IOException.class,
			() -> WeTextureSheet.parse("{ not json", 1024, 1024));
	}
}
