/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import net.wurstclient.other_features.HackListOtf.BarMode;

final class ComposeHackListTest
{
	private static final int LEFT = 40;
	private static final int RIGHT = 140;

	/**
	 * 加这个开关之前，那行代码是
	 * {@code rightAligned ? right - 3 : left + 1}。默认值是 OUTER，所以这里
	 * 逐例钉住它没变——否则「只加设置、不改默认观感」这条就不成立了。
	 */
	@Test
	void outerReproducesThePreExistingPlacementExactly()
	{
		assertEquals(RIGHT - 3,
			ComposeHackList.accentBarX(BarMode.OUTER, LEFT, RIGHT, true));
		assertEquals(LEFT + 1,
			ComposeHackList.accentBarX(BarMode.OUTER, LEFT, RIGHT, false));
	}

	/**
	 * LEFT/RIGHT 是参考 {@code ToggledSettings.BarMode} 的语义：固定侧，
	 * 不随列表靠左还是靠右变化。
	 */
	@Test
	void leftAndRightAreAbsoluteSidesRegardlessOfAlignment()
	{
		for(boolean rightAligned : new boolean[]{true, false})
		{
			assertEquals(LEFT + 1, ComposeHackList.accentBarX(BarMode.LEFT,
				LEFT, RIGHT, rightAligned));
			assertEquals(RIGHT - 3, ComposeHackList.accentBarX(BarMode.RIGHT,
				LEFT, RIGHT, rightAligned));
		}
	}

	@Test
	void noneDrawsNoBar()
	{
		assertNull(ComposeHackList
			.accentBarX(BarMode.NONE, LEFT, RIGHT, true));
		assertNull(ComposeHackList
			.accentBarX(BarMode.NONE, LEFT, RIGHT, false));
	}

	/**
	 * 竖条必须落在条目内部——画到外面会盖住相邻条目或屏幕边缘。
	 */
	@Test
	void everyBarStaysInsideTheEntry()
	{
		for(BarMode mode : BarMode.values())
			for(boolean rightAligned : new boolean[]{true, false})
			{
				Integer accentX = ComposeHackList.accentBarX(mode, LEFT, RIGHT,
					rightAligned);
				if(accentX == null)
					continue;

				String where = mode + " rightAligned=" + rightAligned;
				assertNotNull(accentX, where);
				assertEquals(true, accentX >= LEFT, where);
				assertEquals(true, accentX + ComposeHackList.BAR_WIDTH <= RIGHT,
					where);
			}
	}

	/**
	 * 这个枚举值会被 {@code EnumSetting} 按 {@code toString()} 写进配置，
	 * 所以既是给用户看的名字、也是持久化键。改名等于让旧配置回落默认值，
	 * 这里把它钉住以免被随手改掉。
	 */
	@Test
	void barModeNamesAreStableBecauseTheyArePersisted()
	{
		assertEquals("Outer edge", BarMode.OUTER.toString());
		assertEquals("Left", BarMode.LEFT.toString());
		assertEquals("Right", BarMode.RIGHT.toString());
		assertEquals("None", BarMode.NONE.toString());

		// 默认值必须是 OUTER，否则老用户的观感会变
		assertEquals(BarMode.OUTER, BarMode.values()[0]);
	}
}
