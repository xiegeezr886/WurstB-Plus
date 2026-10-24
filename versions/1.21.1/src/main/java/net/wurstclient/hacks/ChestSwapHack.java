/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"chest swap", "elytra", "chestplate", "AutoElytra"})
public final class ChestSwapHack extends Hack
{
	private static final int CHEST_SLOT = 38;

	private final EnumSetting<Chestplate> chestplate = new EnumSetting<>(
		"Chestplate", "Which type of chestplate to swap to.",
		Chestplate.values(), Chestplate.PREFER_NETHERITE);

	private final CheckboxSetting stayOn = new CheckboxSetting("Stay on",
		"Stays on and swaps back when you turn it off.", false);

	private final CheckboxSetting closeInventory = new CheckboxSetting(
		"Close inventory", "Sends an inventory close packet after swapping.",
		true);

	private boolean swapping;

	public ChestSwapHack()
	{
		super("ChestSwap");
		setCategory(Category.ITEMS);
		addSetting(chestplate);
		addSetting(stayOn);
		addSetting(closeInventory);
	}

	@Override
	protected void onEnable()
	{
		swap();

		if(!stayOn.isChecked())
			setEnabled(false);
	}

	@Override
	protected void onDisable()
	{
		if(stayOn.isChecked())
			swap();
	}

	private void swap()
	{
		if(swapping || MC.player == null || MC.screen != null
			|| MC.player.containerMenu.containerId != 0
			|| !MC.player.containerMenu.getCarried().isEmpty())
			return;

		swapping = true;

		try
		{
			ItemStack current = MC.player.getItemBySlot(EquipmentSlot.CHEST);

			if(current.is(Items.ELYTRA))
				equipChestplate();
			else if(isChestArmor(current))
				equipElytra();
			else if(!equipChestplate())
				equipElytra();

		}finally
		{
			swapping = false;
		}
	}

	private boolean isChestArmor(ItemStack stack)
	{
		return stack.getItem() instanceof ArmorItem armor
			&& armor.getEquipmentSlot() == EquipmentSlot.CHEST;
	}

	private boolean equipChestplate()
	{
		int bestSlot = -1;
		boolean breakLoop = false;

		for(int slot = 0; slot < 36 && !breakLoop; slot++)
		{
			ItemStack stack = MC.player.getInventory().getItem(slot);

			switch(chestplate.getSelected())
			{
				case DIAMOND:
				if(stack.is(Items.DIAMOND_CHESTPLATE))
				{
					bestSlot = slot;
					breakLoop = true;
				}
				break;

				case NETHERITE:
				if(stack.is(Items.NETHERITE_CHESTPLATE))
				{
					bestSlot = slot;
					breakLoop = true;
				}
				break;

				case PREFER_DIAMOND:
				if(stack.is(Items.DIAMOND_CHESTPLATE))
				{
					bestSlot = slot;
					breakLoop = true;

				}else if(stack.is(Items.NETHERITE_CHESTPLATE))
					bestSlot = slot;
				break;

				case PREFER_NETHERITE:
				if(stack.is(Items.DIAMOND_CHESTPLATE))
					bestSlot = slot;
				else if(stack.is(Items.NETHERITE_CHESTPLATE))
				{
					bestSlot = slot;
					breakLoop = true;
				}
				break;
			}
		}

		if(bestSlot != -1)
			equip(bestSlot);

		return bestSlot != -1;
	}

	private boolean equipElytra()
	{
		for(int slot = 0; slot < 36; slot++)
		{
			ItemStack stack = MC.player.getInventory().getItem(slot);

			if(!stack.is(Items.ELYTRA))
				continue;

			equip(slot);
			return true;
		}

		return false;
	}

	/**
	 * Moves the item in the given inventory slot to the chest equipment slot
	 * using pickup clicks: take the new item, click the chest slot (which
	 * swaps out the old item onto the cursor), then put the old item back
	 * into the now-empty source slot.
	 */
	private void equip(int slot)
	{
		int source = InventoryUtils.toNetworkSlot(slot);
		int chest = InventoryUtils.toNetworkSlot(CHEST_SLOT);

		IMC.getInteractionManager().windowClick_PICKUP(source);
		IMC.getInteractionManager().windowClick_PICKUP(chest);
		IMC.getInteractionManager().windowClick_PICKUP(source);

		if(closeInventory.isChecked() && MC.player != null)
			MC.player.connection.send(new ServerboundContainerClosePacket(0));
	}

	public enum Chestplate
	{
		DIAMOND("Diamond"),
		NETHERITE("Netherite"),
		PREFER_DIAMOND("Prefer diamond"),
		PREFER_NETHERITE("Prefer netherite");

		private final String name;

		Chestplate(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
