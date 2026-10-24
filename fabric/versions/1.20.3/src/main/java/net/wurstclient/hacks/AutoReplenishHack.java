/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"auto replenish", "refill", "AutoRefill"})
public final class AutoReplenishHack extends Hack implements UpdateListener
{
	private final SliderSetting minCount = new SliderSetting("Min count",
		"Replenish a hotbar slot when it reaches this item count.", 8, 1,
		63, 1, ValueDisplay.INTEGER);

	private final SliderSetting delay = new SliderSetting("Delay",
		"How long in ticks to wait between replenishing your hotbar.", 1,
		0, 20, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting unstackable = new CheckboxSetting(
		"Unstackable", "Replenish unstackable items.", true);

	private final CheckboxSetting sameEnchants = new CheckboxSetting(
		"Same enchants",
		"Only replace unstackables with items that have the same enchantments.",
		true).visibleWhen(unstackable::isChecked);

	/**
	 * The hotbar items the player had last tick. Index 0-8 are the hotbar from
	 * left to right.
	 */
	private final ItemStack[] items = new ItemStack[9];

	private boolean prevHadOpenScreen;
	private int tickDelayLeft;

	public AutoReplenishHack()
	{
		super("AutoReplenish");
		setCategory(Category.ITEMS);
		addSetting(minCount);
		addSetting(delay);
		addSetting(unstackable);
		addSetting(sameEnchants);
	}

	@Override
	protected void onEnable()
	{
		fillItems();
		tickDelayLeft = delay.getValueI();
		prevHadOpenScreen = MC.screen != null;
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

		if(MC.screen != null)
		{
			prevHadOpenScreen = true;
			return;
		}

		// refresh the snapshot after closing a screen
		if(prevHadOpenScreen)
		{
			fillItems();
			prevHadOpenScreen = false;
		}

		// only work with the player inventory open
		if(MC.player.containerMenu.containerId != 0)
			return;

		// pickup clicks require an empty cursor
		if(!MC.player.containerMenu.getCarried().isEmpty())
			return;

		if(tickDelayLeft > 0)
		{
			tickDelayLeft--;
			return;
		}

		for(int slot = 0; slot < 9; slot++)
			checkSlot(slot);

		tickDelayLeft = delay.getValueI();
	}

	private void checkSlot(int slot)
	{
		Inventory inventory = MC.player.getInventory();
		ItemStack stack = inventory.getItem(slot);
		ItemStack prevStack = items[slot];
		items[slot] = stack.copy();

		if(stack.isEmpty() && prevStack.isEmpty())
			return;

		int threshold = minCount.getValueI();
		int fromSlot = -1;

		// stack is still there but just crossed the threshold
		if(!stack.isEmpty() && stack.getMaxStackSize() > 1
			&& stack.getCount() <= threshold)
			fromSlot = findItem(stack, slot,
				threshold - stack.getCount() + 1, true);

		// stack went from above the threshold to empty in a single tick
		if(fromSlot == -1 && !prevStack.isEmpty()
			&& prevStack.getMaxStackSize() > 1 && stack.isEmpty())
			fromSlot = findItem(prevStack, slot, threshold + 1, false);

		// unstackable items (tools, armor, etc.)
		if(fromSlot == -1 && unstackable.isChecked()
			&& !prevStack.isEmpty() && prevStack.getMaxStackSize() == 1
			&& stack.isEmpty())
			fromSlot = findItem(prevStack, slot, 1, false);

		if(fromSlot == -1)
			return;

		moveToHotbar(fromSlot, slot);
		items[slot] = inventory.getItem(slot).copy();
	}

	private int findItem(ItemStack lookFor, int excludedSlot,
		int goodEnoughCount, boolean mustCombine)
	{
		int bestSlot = -1;
		int bestCount = 0;

		// search the main inventory, from the bottom up
		for(int slot = 35; slot >= 9; slot--)
		{
			if(slot == excludedSlot)
				continue;

			ItemStack stack = MC.player.getInventory().getItem(slot);
			if(stack.isEmpty() || stack.getItem() != lookFor.getItem())
				continue;

			if(mustCombine
				&& !ItemStack.isSameItemSameTags(lookFor, stack))
				continue;

			if(sameEnchants.isChecked()
				&& !EnchantmentHelper.getEnchantments(stack)
					.equals(EnchantmentHelper.getEnchantments(lookFor)))
				continue;

			if(stack.getCount() > bestCount)
			{
				bestSlot = slot;
				bestCount = stack.getCount();

				if(bestCount >= goodEnoughCount)
					break;
			}
		}

		return bestSlot;
	}

	/**
	 * Merges the stack in {@code fromSlot} into the hotbar slot
	 * {@code hotbarSlot} using three pickup clicks: take the source stack,
	 * place it onto the hotbar stack (or into the empty slot), then put any
	 * remainder back into the source slot.
	 */
	private void moveToHotbar(int fromSlot, int hotbarSlot)
	{
		int source = InventoryUtils.toNetworkSlot(fromSlot);
		int target = InventoryUtils.toNetworkSlot(hotbarSlot);

		IMC.getInteractionManager().windowClick_PICKUP(source);
		IMC.getInteractionManager().windowClick_PICKUP(target);
		IMC.getInteractionManager().windowClick_PICKUP(source);
	}

	private void fillItems()
	{
		for(int slot = 0; slot < 9; slot++)
		{
			if(MC.player == null)
			{
				items[slot] = ItemStack.EMPTY;
				continue;
			}

			items[slot] = MC.player.getInventory().getItem(slot).copy();
		}
	}
}
