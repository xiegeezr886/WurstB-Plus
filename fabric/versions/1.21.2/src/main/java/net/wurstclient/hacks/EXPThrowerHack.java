/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"exp thrower", "xp thrower", "auto exp", "experience bottle"})
public final class EXPThrowerHack extends Hack implements UpdateListener
{
	public EXPThrowerHack()
	{
		super("EXPThrower");
		setCategory(Category.ITEMS);
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
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		int slot = InventoryUtils.indexOf(Items.EXPERIENCE_BOTTLE, 9);
		if(slot == -1)
			return;

		InventoryUtils.selectItem(slot);
		if(MC.player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE))
			MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
	}
}
