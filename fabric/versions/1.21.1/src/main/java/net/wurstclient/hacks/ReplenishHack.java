/*
 * Copyright (c) 2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */

/* Ported from LeavesHack (AGPL-3.0). */
package net.wurstclient.hacks;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

/**
 * Simplified port of LeavesHack's Replenish. The original snapshots the whole
 * hotbar and refills any slot whose item changed; this version refills the
 * currently held stack (or the item that was there before it ran out) from the
 * main inventory once its count drops to the configured threshold.
 */
@SearchTags({"replenish", "refill", "auto refill"})
public final class ReplenishHack extends Hack implements UpdateListener
{
	private final SliderSetting minCount = new SliderSetting("Min count",
		"Refill the held stack when its count reaches this value.", 8, 1, 63,
		1, ValueDisplay.INTEGER);

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks to wait between refill attempts.", 1, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting blocksOnly = new CheckboxSetting(
		"Blocks only",
		"Only refill held block stacks, leaving tools and other items alone.",
		true);

	private final CheckboxSetting pauseWhileUsing = new CheckboxSetting(
		"Pause while using", "Pauses while you are using an item.", true);

	private final ItemStack[] lastHeld = new ItemStack[9];

	private int tickDelayLeft;

	public ReplenishHack()
	{
		super("Replenish");
		setCategory(Category.ITEMS);
		addSetting(minCount);
		addSetting(delay);
		addSetting(blocksOnly);
		addSetting(pauseWhileUsing);
	}

	@Override
	protected void onEnable()
	{
		fillLastHeld();
		tickDelayLeft = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		clearLastHeld();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
			return;

		if(MC.screen != null || MC.player.containerMenu.containerId != 0
			|| !MC.player.containerMenu.getCarried().isEmpty()
			|| MC.player.getAbilities().instabuild)
			return;

		if(pauseWhileUsing.isChecked() && MC.options.keyUse.isDown())
			return;

		if(tickDelayLeft > 0)
		{
			tickDelayLeft--;
			return;
		}

		Inventory inventory = MC.player.getInventory();
		int slot = inventory.selected;
		ItemStack current = inventory.getItem(slot);
		ItemStack previous = lastHeld[slot];

		ItemStack wanted = current.isEmpty() ? previous : current;
		if(!shouldReplenish(current, wanted, slot))
		{
			lastHeld[slot] = current.copy();
			return;
		}

		int fromSlot = findItem(wanted, slot);
		if(fromSlot == -1)
		{
			lastHeld[slot] = current.copy();
			return;
		}

		moveToHotbar(fromSlot, slot);
		tickDelayLeft = delay.getValueI();
		lastHeld[slot] = inventory.getItem(slot).copy();
	}

	private boolean shouldReplenish(ItemStack current, ItemStack wanted,
		int slot)
	{
		if(wanted.isEmpty() || wanted.getMaxStackSize() <= 1)
			return false;
		if(blocksOnly.isChecked() && !(wanted.getItem() instanceof BlockItem))
			return false;

		int threshold = minCount.getValueI();
		if(current.isEmpty())
			return true;
		if(current.getCount() > threshold)
			return false;

		// Don't bother when there is no matching stack left.
		return findItem(wanted, slot) != -1;
	}

	private int findItem(ItemStack lookFor, int excludedSlot)
	{
		int bestSlot = -1;
		int bestCount = 0;

		for(int slot = 35; slot >= 9; slot--)
		{
			if(slot == excludedSlot)
				continue;

			ItemStack stack = MC.player.getInventory().getItem(slot);
			if(stack.isEmpty()
				|| !ItemStack.isSameItemSameComponents(lookFor, stack))
				continue;

			if(stack.getCount() > bestCount)
			{
				bestSlot = slot;
				bestCount = stack.getCount();
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

	private void fillLastHeld()
	{
		for(int slot = 0; slot < 9; slot++)
		{
			if(MC.player == null)
			{
				lastHeld[slot] = ItemStack.EMPTY;
				continue;
			}

			lastHeld[slot] =
				MC.player.getInventory().getItem(slot).copy();
		}
	}

	private void clearLastHeld()
	{
		for(int slot = 0; slot < 9; slot++)
			lastHeld[slot] = ItemStack.EMPTY;
	}
}
