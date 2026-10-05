/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gui.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.wurstclient.WurstClient;

/**
 * Tests the texture name the picker builds for a background thumbnail.
 *
 * <p>
 * This is a regression test for a real crash: workshop entries are named after
 * their title, so their ids can be non-ASCII, and {@code ResourceLocation}
 * rejects anything outside {@code [a-z0-9/._-]}. Rendering the picker with such
 * an entry in it threw and took the client down
 * (crash-2026-10-05_15.15.30, entry "arknights-明日方舟-阿米娅-王冠-…").
 */
final class BackgroundThumbnailNameTest
{
	@Test
	void nonAsciiIdsProduceAValidLocation()
	{
		String id = "arknights-\u660E\u65E5\u65B9\u821F-\u963F\u7C73\u5A05";
		String name = BackgroundSelectScreen.thumbnailTextureName(id);

		// must not throw
		ResourceLocation location =
			new ResourceLocation(WurstClient.MOD_ID, name);

		assertEquals(WurstClient.MOD_ID, location.getNamespace());
		assertEquals(name, location.getPath());
	}

	@Test
	void theOriginalIdStillShowsUpInTheName()
	{
		String name = BackgroundSelectScreen.thumbnailTextureName(
			"persica-2024");
		assertTrue(name.startsWith("background_thumb/persica-2024_"), name);
	}

	@Test
	void illegalCharactersAreReplaced()
	{
		String name = BackgroundSelectScreen
			.thumbnailTextureName("a b/c\\d:e?f*g\"h<i>j|k");

		assertTrue(name.startsWith("background_thumb/a_b_c_d_e_f_g_h_i_j_k_"),
			name);
	}

	@Test
	void differentIdsDoNotCollide()
	{
		String first =
			BackgroundSelectScreen.thumbnailTextureName("\u65E5\u672C\u8A9E");
		String second =
			BackgroundSelectScreen.thumbnailTextureName("\u4E2D\u6587\u5B57");

		// both would sanitise to the same letters/underscores; the hash keeps
		// them apart
		assertNotEquals(first, second);
	}

	@Test
	void longIdsAreTruncatedButStillUnique()
	{
		String long1 = "x".repeat(200) + "-one";
		String long2 = "x".repeat(200) + "-two";

		String first = BackgroundSelectScreen.thumbnailTextureName(long1);
		String second = BackgroundSelectScreen.thumbnailTextureName(long2);

		assertTrue(first.length() < 80, "没截断：" + first.length());
		assertNotEquals(first, second);
	}
}
