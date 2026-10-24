/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"auto mend", "mending", "AutoRepair"})
public final class AutoMendHack extends Hack implements UpdateListener
{
	private static final int OFFHAND_SLOT = 40;

	private final CheckboxSetting force = new CheckboxSetting("Force",
		"Replaces an item in the offhand even if it is not repairable.",
		false);

	private final CheckboxSetting autoDisable = new CheckboxSetting(
		"Auto disable",
		"Automatically disables when there are no more items to repair.",
		true);

	private final SliderSetting delay = new SliderSetting("Delay",
		"Amount of ticks to wait before performing the next inventory move.",
		2, 0, 20, 1, ValueDisplay.INTEGER);

	private int timer;
	private boolean didMove;

	public AutoMendHack()
	{
		super("AutoMend");
		setCategory(Category.ITEMS);
		addSetting(force);
		addSetting(autoDisable);
		addSetting(delay);
	}

	@Override
	protected void onEnable()
	{
		timer = 0;
		didMove = false;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		timer = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.player.isSpectator())
			return;

		// don't move items while a container is open
		if(MC.screen != null || MC.player.containerMenu.containerId != 0)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		ItemStack offhand = MC.player.getOffhandItem();
		if(!offhand.isEmpty())
		{
			if(isMending(offhand))
			{
				// wait until the offhand item is fully repaired
				if(offhand.getDamageValue() > 0)
					return;

			}else if(!force.isChecked())
				return;
		}

		int slot = findDamagedMendingItem();
		if(slot != -1)
		{
			IMC.getInteractionManager().windowClick_SWAP(
				InventoryUtils.toNetworkSlot(slot), OFFHAND_SLOT);
			didMove = true;
			timer = delay.getValueI();
			return;
		}

		if(!autoDisable.isChecked())
			return;

		// free up the offhand before disabling
		if(didMove)
		{
			int emptySlot = findEmptySlot();
			if(emptySlot != -1 && !MC.player.getOffhandItem().isEmpty())
			{
				IMC.getInteractionManager().windowClick_SWAP(
					InventoryUtils.toNetworkSlot(emptySlot),
					OFFHAND_SLOT);
				timer = delay.getValueI();
			}
		}

		setEnabled(false);
	}

	private int findDamagedMendingItem()
	{
		for(int slot = 0; slot < 36; slot++)
		{
			ItemStack stack = MC.player.getInventory().getItem(slot);

			if(stack.isEmpty() || !isMending(stack)
				|| stack.getDamageValue() <= 0)
				continue;

			return slot;
		}

		return -1;
	}

	private int findEmptySlot()
	{
		for(int slot = 0; slot < 36; slot++)
			if(MC.player.getInventory().getItem(slot).isEmpty())
				return slot;

		return -1;
	}

	private boolean isMending(ItemStack stack)
	{
		return EnchantmentHelper.getItemEnchantmentLevel(
			Enchantments.MENDING, stack) > 0;
	}
}
