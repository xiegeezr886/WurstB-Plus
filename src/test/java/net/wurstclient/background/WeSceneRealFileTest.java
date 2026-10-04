/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Runs the scene readers against a real Wallpaper Engine package.
 *
 * <p>
 * Every other test in this package builds its fixtures here, which is what
 * makes them hermetic - but a fixture can only prove that the reader agrees with
 * what I believed the format to be. This one exists because a belief was already
 * wrong once: an earlier attempt read the version strings as length prefixed,
 * concluded that the fourteen megabyte package contained no textures at all, and
 * wrote that conclusion into the class documentation.
 *
 * <p>
 * The file belongs to Steam and cannot be committed, so the test skips itself
 * when it is not installed. Point {@code -Dwurstb.scene.pkg=...} at one to run
 * it elsewhere.
 */
final class WeSceneRealFileTest
{
	/** Wallpaper Engine's app id, then the Persica workshop id. */
	private static final String DEFAULT_PACKAGE =
		"C:\\Program Files (x86)\\Steam\\steamapps\\workshop\\content\\431960"
			+ "\\2359043440\\scene.pkg";

	private static Path packageFile()
	{
		String override = System.getProperty("wurstb.scene.pkg");
		return Path.of(override != null ? override : DEFAULT_PACKAGE);
	}

	@Test
	void itReadsTheRealPackage() throws IOException
	{
		Path file = packageFile();
		Assumptions.assumeTrue(Files.isRegularFile(file),
			"no Wallpaper Engine scene installed at " + file);

		WePackage pkg = WePackage.read(file);

		assertEquals("PKGV0013", pkg.version());
		assertTrue(pkg.names().contains(WeScene.SCENE_JSON));

		WeScene scene = WeScene.parse(
			new String(pkg.read(WeScene.SCENE_JSON),
				java.nio.charset.StandardCharsets.UTF_8),
			name -> {
				byte[] bytes = pkg.read(name);
				return bytes == null ? null
					: new String(bytes,
						java.nio.charset.StandardCharsets.UTF_8);
			});

		// 作者是在 4K 画布上摆的，缩略图看也是这个取景
		assertEquals(3840, scene.width());
		assertEquals(2160, scene.height());
		assertEquals(1.0F, scene.zoom(), 0.0001F);

		// 20 个对象里只有这三层真的要画
		assertEquals(3, scene.layers().size());
		assertEquals(List.of("Blossom backdrop", "backdrop flowers", "Branch"),
			scene.layers().stream().map(WeScene.Layer::name).toList());

		// 每一层都要能在包里找到自己的 .tex，并解出图片载荷
		for(WeScene.Layer layer : scene.layers())
		{
			String entry =
				WeScene.textureEntryName(layer.texture(), pkg.names());
			assertNotNull(entry, layer.name() + " 的贴图没找到：" + layer.texture());

			WeTexture texture = WeTexture.parse(pkg.read(entry));
			assertTrue(texture.isStandardImage(),
				entry + " 的载荷既不是 PNG 也不是 JPEG");
			assertEquals(layer.sizeX(), texture.imageWidth(), 0.0001F);
			assertEquals(layer.sizeY(), texture.imageHeight(), 0.0001F);
		}
	}
}
