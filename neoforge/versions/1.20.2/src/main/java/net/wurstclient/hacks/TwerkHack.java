/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;

@SearchTags({"twirk", "dance", "crouch"})
public final class TwerkHack extends Hack implements UpdateListener
{
	private int tick;

	public TwerkHack()
	{
		super("Twerk");
		setCategory(Category.FUN);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		MC.options.keyShift.setDown(false);
	}

	@Override
	public void onUpdate()
	{
		tick++;
		MC.options.keyShift.setDown(tick % 4 < 2);
	}
}
