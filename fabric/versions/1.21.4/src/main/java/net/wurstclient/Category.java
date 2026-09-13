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
	BLOCKS("\u65B9\u5757"),
	MOVEMENT("\u79FB\u52A8"),
	COMBAT("\u6218\u6597"),
	RENDER("\u6E32\u67D3"),
	CHAT("\u804A\u5929"),
	FUN("\u5A31\u4E50"),
	ITEMS("\u7269\u54C1"),
	OTHER("\u5176\u4ED6");

	private final String name;

	private Category(String name)
	{
		this.name = name;
	}

	public String getName()
	{
		return name;
	}
}
