/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient;

public enum Category
{
	COMBAT("战斗", 0xFF6B6B),
	PLAYER("玩家", 0xFFA94D),
	MOVEMENT("移动", 0x4DABF7),
	RENDER("渲染", 0xB197FC),
	WORLD("世界", 0x69DB7C),
	MISC("其他", 0x38D9A9),
	EXPLOIT("漏洞", 0xF783AC),
	FUN("娱乐", 0xFFD43B),
	// HUD widgets remain a separate editor group, outside the gameplay list.
	HUD("HUD", 0xB5BEF0);

	private final String name;
	private final int color;

	private Category(String name, int color)
	{
		this.name = name;
		this.color = color;
	}

	/** RedFix's category color used by its ArrayList theme. */
	public int getColor() { return color; }

	public String getName()
	{
		return name;
	}
}
