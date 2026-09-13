/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hack;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class HackDisplayNameTest
{
	@Test
	void localizesPlainRenderName()
	{
		assertEquals("杀戮光环",
			Hack.localizeRenderName("Killaura", "杀戮光环", "Killaura"));
	}

	@Test
	void preservesDynamicRenderSuffix()
	{
		assertEquals("飞行 [Vanilla]",
			Hack.localizeRenderName("Flight", "飞行", "Flight [Vanilla]"));
	}

	@Test
	void preservesUnrelatedSpecialRenderName()
	{
		assertEquals("X-Wurst",
			Hack.localizeRenderName("X-Ray", "矿物透视", "X-Wurst"));
	}
}
