/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hacks;

import java.lang.reflect.Method;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;

/**
 * Enables Minecraft's native raw mouse input option.
 *
 * <p>Soar used a JInput thread. Minecraft 1.20.2+ already exposes the platform
 * raw input path, so this port toggles and restores that option. The option was
 * moved/renamed in some 26.x builds, so the lookup is reflective.
 */
@SearchTags({"raw input", "rawinput", "raw mouse", "mouse input"})
public final class RawInputHack extends Hack
{
	private Object option;
	private Object oldValue;

	public RawInputHack()
	{
		super("RawInput");
		setCategory(Category.OTHER);
	}

	@Override
	protected void onEnable()
	{
		option = null;
		oldValue = null;
		try
		{
			Method getter = MC.options.getClass().getMethod("rawMouseInput");
			option = getter.invoke(MC.options);
			oldValue = option.getClass().getMethod("get").invoke(option);
			option.getClass().getMethod("set", Object.class).invoke(option,
				true);
		}catch(Exception ignored)
		{
			option = null;
			oldValue = null;
		}
	}

	@Override
	protected void onDisable()
	{
		if(option == null)
			return;

		try
		{
			option.getClass().getMethod("set", Object.class).invoke(option,
				oldValue);
		}catch(Exception ignored)
		{}

		option = null;
		oldValue = null;
	}
}
