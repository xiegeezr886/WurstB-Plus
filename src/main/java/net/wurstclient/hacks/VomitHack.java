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
import net.wurstclient.mixinterface.IKeyBinding;

@SearchTags({"puke", "barf", "food"})
public final class VomitHack extends Hack implements UpdateListener
{
	private int tick;

	public VomitHack()
	{
		super("Vomit");
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
		// 用 resetPressedState() 按玩家真实按键状态恢复，而不是
		// setDown(false)：后者会把玩家此刻真按着的右键一并取消。
		IKeyBinding.get(MC.options.keyUse).resetPressedState();
	}

	@Override
	public void onUpdate()
	{
		tick++;
		if(tick % 3 == 0)
			MC.options.keyUse.setDown(true);
		else
			MC.options.keyUse.setDown(false);
	}
}
