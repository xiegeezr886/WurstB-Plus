/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * 真实作品里的粒子帧格：走完整的装载通路，逐个粒子层核对"列×行、帧数"。
 *
 * <p>
 * 合成用例只能证明"我按我理解的格式读"，证明不了"格式真的是那样"——
 * 这一条判断已经错过两次：先是断言"包里没有帧格元数据"（其实元数据是贴图旁边的
 * {@code .tex-json}，不在包内，在 WE 安装目录里），又是把 {@code fog3} 的帧格
 * 看成 16×8。所以这里拿"佩丽卡"（工坊 3399479885）的真包跑一遍：</p>
 *
 * <pre>
 * 两层 Fog (calm) → particle/fog/fog1  1024x1024  spritesheetsequences 64 帧 128x128 → 8x8
 * 一层 Fog (gust) → particle/fog/fog3  1024x1024  同上                              → 8x8
 * 一层 Stars      → particle/halo_6     128x128   没有这个键                        → 单帧
 * </pre>
 *
 * <p>
 * 包是 Steam 的、贴图是 WE 安装目录的，都不能进版本库，所以没装时这条测试自己跳过
 * （{@code -Dwurstb.scene.pkg=…} 可以指到别处）。</p>
 */
final class WeParticleSheetRealFileTest
{
	/** 佩丽卡：工坊 3399479885。 */
	private static final String DEFAULT_PACKAGE =
		"C:\\Program Files (x86)\\Steam\\steamapps\\workshop\\content\\431960"
			+ "\\3399479885\\scene.pkg";

	private static Path packageFile()
	{
		String override = System.getProperty("wurstb.scene.pkg");
		return Path.of(override != null ? override : DEFAULT_PACKAGE);
	}

	@Test
	void itReadsTheDeclaredFrameGrids() throws IOException
	{
		Path file = packageFile();
		Assumptions.assumeTrue(Files.isRegularFile(file),
			"no Wallpaper Engine scene installed at " + file);
		Assumptions.assumeTrue(SteamLocator.wallpaperEngineAssets() != null,
			"Wallpaper Engine assets (the .tex-json sidecars) not installed");

		WePackage pkg = WePackage.read(file);
		WeScene scene = WeScene.parse(
			new String(pkg.read(WeScene.SCENE_JSON), StandardCharsets.UTF_8),
			name -> {
				byte[] bytes = pkg.read(name);
				return bytes == null ? null
					: new String(bytes, StandardCharsets.UTF_8);
			});

		List<WeSceneWallpaper.DecodedParticles> decoded =
			WeSceneWallpaper.decodeParticles(pkg, scene);

		Map<String, String> grids = new LinkedHashMap<>();

		for(WeSceneWallpaper.DecodedParticles layer : decoded)
		{
			String texture = layer.sprite() == null ? "(无材质)"
				: layer.sprite().texture();
			String size = layer.spriteImage() == null ? "无贴图"
				: layer.spriteImage().getWidth() + "x"
					+ layer.spriteImage().getHeight();
			WeParticleSheet sheet = layer.sheet();
			String grid = sheet.columns() + "x" + sheet.rows() + " frames="
				+ sheet.frames();

			System.out.println("[Probe] " + layer.layer().name() + " | "
				+ texture + " | " + size + " | " + grid);

			grids.put(texture, grid);
		}

		// 两张雾贴图都必须在**同一趟**装载里被认成 8×8/64：这是这一轮的目标
		assertEquals("8x8 frames=64", grids.get("particle/fog/fog1"));
		assertEquals("8x8 frames=64", grids.get("particle/fog/fog3"));

		// halo 系列没有声明帧序列，必须仍是单帧
		if(grids.containsKey("particle/halo_6"))
			assertEquals("1x1 frames=1", grids.get("particle/halo_6"));

		assertTrue(grids.size() >= 3,
			"佩丽卡至少有三层粒子，实际只解出 " + grids);
	}

	/** 帧宽必须整除贴图宽、帧高整除贴图高，否则行优先取值会错位。 */
	@Test
	void everySheetDividesItsTextureEvenly() throws IOException
	{
		Path file = packageFile();
		Assumptions.assumeTrue(Files.isRegularFile(file),
			"no Wallpaper Engine scene installed at " + file);
		Assumptions.assumeTrue(SteamLocator.wallpaperEngineAssets() != null,
			"Wallpaper Engine assets not installed");

		WePackage pkg = WePackage.read(file);
		WeScene scene = WeScene.parse(
			new String(pkg.read(WeScene.SCENE_JSON), StandardCharsets.UTF_8),
			name -> {
				byte[] bytes = pkg.read(name);
				return bytes == null ? null
					: new String(bytes, StandardCharsets.UTF_8);
			});

		for(WeSceneWallpaper.DecodedParticles layer : WeSceneWallpaper
			.decodeParticles(pkg, scene))
		{
			WeParticleSheet sheet = layer.sheet();

			if(layer.spriteImage() == null || !sheet.isSheet())
				continue;

			assertEquals(0, layer.spriteImage().getWidth() % sheet.columns(),
				layer.sprite().texture() + " 的列数除不尽贴图宽");
			assertEquals(0, layer.spriteImage().getHeight() % sheet.rows(),
				layer.sprite().texture() + " 的行数除不尽贴图高");
		}
	}
}
