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
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.Rotation;

@SearchTags({"exp thrower", "xp thrower", "auto exp", "experience bottle"})
public final class EXPThrowerHack extends Hack implements UpdateListener
{
	private final CheckboxSetting lookDown = new CheckboxSetting("Look down",
		"Throw bottles at your feet, then restore your view.", true);
	private final CheckboxSetting restoreSlot = new CheckboxSetting("Restore slot",
		"Restore the selected hotbar slot after throwing.", true);

	public EXPThrowerHack()
	{
		super("EXPThrower");
		setCategory(Category.ITEMS);
		addSetting(lookDown);
		addSetting(restoreSlot);
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
		if(MC.player == null || MC.level == null || MC.gameMode == null || MC.screen != null)
			return;

		var player = MC.player;
		InteractionHand hand = player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE)
			? InteractionHand.MAIN_HAND : player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE)
				? InteractionHand.OFF_HAND : null;
		int slot = hand == null ? InventoryUtils.indexOf(Items.EXPERIENCE_BOTTLE, 9) : -1;
		if(hand == null && slot == -1)
			return;

		int oldSlot = player.getInventory().getSelectedSlot();
		float oldYaw = player.getYRot();
		float oldPitch = player.getXRot();
		boolean rotate = lookDown.isChecked();
		try
		{
			if(hand == null)
			{
				InventoryUtils.selectItem(slot);
				hand = InteractionHand.MAIN_HAND;
			}
			if(rotate)
			{
				Rotation down = new Rotation(oldYaw, 90);
				down.applyToClientPlayer();
				down.sendPlayerLookPacket();
			}
			MC.gameMode.useItem(player, hand);
		}
		finally
		{
			if(restoreSlot.isChecked())
				player.getInventory().setSelectedSlot(oldSlot);
			if(rotate)
			{
				player.setYRot(oldYaw);
				player.setXRot(oldPitch);
				if(MC.player == player && MC.getConnection() != null)
					new Rotation(oldYaw, oldPitch).sendPlayerLookPacket();
			}
		}
	}
}
