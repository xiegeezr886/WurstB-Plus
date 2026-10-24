/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import java.util.ArrayDeque;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IClientPlayerInteractionManager;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"inventory tweaks", "InventorySorter", "inventory sorter",
	"sort inventory", "merge stacks", "ItemSorter", "auto sort"})
public final class InventoryTweaksHack extends Hack implements UpdateListener
{
	// Network slot ids inside the player's inventory menu. These are the same
	// ids that IClientPlayerInteractionManager's window click helpers expect.
	private static final int MAIN_START = InventoryMenu.INV_SLOT_START;
	private static final int MAIN_END = InventoryMenu.INV_SLOT_END;
	private static final int HOTBAR_START = InventoryMenu.USE_ROW_SLOT_START;
	private static final int HOTBAR_END = InventoryMenu.USE_ROW_SLOT_END;

	private static final int OP_SWAP = 0;
	private static final int OP_MOVE = 1;

	private final CheckboxSetting sortOnOpen = new CheckboxSetting(
		"Sort on open", "Sorts the player inventory when it is opened.",
		true);

	private final CheckboxSetting mergeStacks = new CheckboxSetting(
		"Merge stacks", "Merges identical item stacks while sorting.", true);

	private final CheckboxSetting sortHotbar = new CheckboxSetting(
		"Sort hotbar",
		"Also sorts the hotbar, separately from the main inventory.", false);

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks to wait between inventory clicks.", 2, 1, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final ArrayDeque<Integer> pendingClicks = new ArrayDeque<>();
	private int clickIndex;
	private int delayTimer;
	private boolean sessionActive;
	private boolean wasOpen;

	public InventoryTweaksHack()
	{
		super("InventoryTweaks");
		setCategory(Category.ITEMS);
		addSetting(sortOnOpen);
		addSetting(mergeStacks);
		addSetting(sortHotbar);
		addSetting(delay);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		reset();
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		reset();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
		{
			reset();
			return;
		}

		boolean open = MC.screen instanceof InventoryScreen
			&& MC.player.containerMenu == MC.player.inventoryMenu;
		if(!open)
		{
			reset();
			return;
		}

		if(!wasOpen)
		{
			wasOpen = true;
			if(sortOnOpen.isChecked())
				sessionActive = true;
		}

		if(!sessionActive)
			return;

		AbstractContainerMenu menu = MC.player.inventoryMenu;

		if(delayTimer > 0)
		{
			delayTimer--;
			return;
		}

		// Never start a new sequence while the player is holding a stack.
		if(pendingClicks.isEmpty() && !menu.getCarried().isEmpty())
			return;

		if(pendingClicks.isEmpty())
		{
			planNextAction(menu);
			if(pendingClicks.isEmpty())
			{
				sessionActive = false;
				return;
			}
		}

		int slot = pendingClicks.poll();
		boolean expectCarried = clickIndex > 0;
		if(menu.getCarried().isEmpty() == expectCarried
			|| !isAllowedSlot(slot))
		{
			// Something interfered with the sequence. Stop rather than risk
			// moving an item into the wrong slot.
			resetSequence();
			sessionActive = false;
			return;
		}

		IClientPlayerInteractionManager im =
			(IClientPlayerInteractionManager)IMC.getInteractionManager();
		im.windowClick_PICKUP(slot);

		clickIndex++;
		delayTimer = delay.getValueI();
		if(pendingClicks.isEmpty())
			clickIndex = 0;
	}

	private void planNextAction(AbstractContainerMenu menu)
	{
		if(planGroup(menu, MAIN_START, MAIN_END))
			return;

		if(sortHotbar.isChecked())
			planGroup(menu, HOTBAR_START, HOTBAR_END);
	}

	private boolean planGroup(AbstractContainerMenu menu, int from, int to)
	{
		if(mergeStacks.isChecked())
		{
			int[] merge = findMerge(menu, from, to);
			if(merge != null)
			{
				pendingClicks.add(merge[0]);
				pendingClicks.add(merge[1]);
				clickIndex = 0;
				return true;
			}
		}

		int[] operation = findSortOperation(menu, from, to);
		if(operation == null)
			return false;

		pendingClicks.add(operation[1]);
		pendingClicks.add(operation[2]);
		if(operation[0] == OP_SWAP)
			pendingClicks.add(operation[1]);

		clickIndex = 0;
		return true;
	}

	/**
	 * Finds two stacks that can be combined without leaving a remainder on the
	 * cursor. Returns {@code {sourceSlot, targetSlot}}.
	 */
	private int[] findMerge(AbstractContainerMenu menu, int from, int to)
	{
		for(int i = from; i < to; i++)
		{
			ItemStack target = menu.getSlot(i).getItem();
			if(target.isEmpty()
				|| target.getCount() >= target.getMaxStackSize())
				continue;

			for(int j = i + 1; j < to; j++)
			{
				ItemStack source = menu.getSlot(j).getItem();
				if(source.isEmpty() || !ItemStack
					.isSameItemSameTags(target, source))
					continue;

				if(target.getCount()
					+ source.getCount() > target.getMaxStackSize())
					continue;

				return new int[]{j, i};
			}
		}

		return null;
	}

	/**
	 * Returns {@code {OP_SWAP, firstSlot, secondSlot}} or
	 * {@code {OP_MOVE, sourceSlot, emptySlot}}, or {@code null} if this group
	 * is already sorted and compacted.
	 */
	private int[] findSortOperation(AbstractContainerMenu menu, int from,
		int to)
	{
		for(int i = from; i < to; i++)
		{
			ItemStack a = menu.getSlot(i).getItem();

			if(a.isEmpty())
			{
				for(int j = i + 1; j < to; j++)
					if(!menu.getSlot(j).getItem().isEmpty())
						return new int[]{OP_MOVE, j, i};
				continue;
			}

			for(int j = i + 1; j < to; j++)
			{
				ItemStack b = menu.getSlot(j).getItem();
				if(b.isEmpty())
					continue;

				if(compareStacks(a, b) > 0)
					return new int[]{OP_SWAP, i, j};
			}
		}

		return null;
	}

	private int compareStacks(ItemStack a, ItemStack b)
	{
		String idA =
			BuiltInRegistries.ITEM.getKey(a.getItem()).toString();
		String idB =
			BuiltInRegistries.ITEM.getKey(b.getItem()).toString();

		int comparison = idA.compareTo(idB);
		if(comparison != 0)
			return comparison;

		comparison = Integer.compare(b.getCount(), a.getCount());
		if(comparison != 0)
			return comparison;

		return a.getHoverName().getString()
			.compareTo(b.getHoverName().getString());
	}

	private boolean isAllowedSlot(int slot)
	{
		if(MC.player == null
			|| MC.player.containerMenu != MC.player.inventoryMenu)
			return false;

		if(slot < 0 || slot >= MC.player.inventoryMenu.slots.size())
			return false;

		if(slot >= MAIN_START && slot < MAIN_END)
			return true;

		return sortHotbar.isChecked() && slot >= HOTBAR_START
			&& slot < HOTBAR_END;
	}

	private void resetSequence()
	{
		pendingClicks.clear();
		clickIndex = 0;
		delayTimer = 0;
	}

	private void reset()
	{
		resetSequence();
		sessionActive = false;
		wasOpen = false;
	}
}
