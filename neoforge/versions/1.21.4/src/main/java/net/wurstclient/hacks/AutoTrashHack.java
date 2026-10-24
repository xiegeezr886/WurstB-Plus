/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from FO (CC0-1.0).
 */
package net.wurstclient.hacks;

import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IClientPlayerInteractionManager;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

/**
 * Simplified port of FO's AutoTrash module.
 *
 * <p>
 * Drops unwanted items from the player's main inventory in batches, one scan
 * every {@code Delay} ticks. The {@code Keep items} list always wins over the
 * {@code Items} list, so a valuable item can be protected even if it also
 * matches a broad filter entry. The currently selected hotbar slot is never
 * touched.
 *
 * <p>
 * Unlike FO's version this port has no whitelist mode, enchantment book rules
 * or automatic addon linking.
 */
@SearchTags({"auto trash", "AutoDrop", "auto drop", "trash", "item filter",
	"inventory cleaner", "InvCleaner"})
public final class AutoTrashHack extends Hack implements UpdateListener
{
	private final ItemListSetting items = new ItemListSetting("Items",
		"Unwanted items that will be dropped.", "minecraft:poisonous_potato",
		"minecraft:rotten_flesh", "minecraft:spider_eye",
		"minecraft:stick", "minecraft:wheat_seeds");

	private final ItemListSetting keepItems = new ItemListSetting(
		"Keep items",
		"Items that will never be dropped, even if they also appear in the"
			+ " Items list.");

	private final SliderSetting delay =
		new SliderSetting("Delay", "How often the inventory is scanned.", 20, 0,
			200, 5, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private int timer;

	public AutoTrashHack()
	{
		super("AutoTrash");
		setCategory(Category.ITEMS);
		addSetting(items);
		addSetting(keepItems);
		addSetting(delay);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		timer = 0;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		// Network slot IDs only line up while the player's own inventory is
		// showing. Any other container screen would make us click wrong slots.
		if(MC.screen instanceof AbstractContainerScreen
			&& !(MC.screen instanceof InventoryScreen))
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		List<String> filter = items.getItemNames();
		if(filter.isEmpty())
			return;

		List<String> keep = keepItems.getItemNames();
		int selectedSlot = MC.player.getInventory().selected;
		boolean dropped = false;

		for(int slot = 0; slot < 36; slot++)
		{
			if(slot == selectedSlot)
				continue;

			ItemStack stack = MC.player.getInventory().getItem(slot);
			if(stack.isEmpty())
				continue;

			String itemName =
				BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

			if(!filter.contains(itemName) || keep.contains(itemName))
				continue;

			((IClientPlayerInteractionManager)IMC.getInteractionManager())
				.windowClick_THROW(InventoryUtils.toNetworkSlot(slot));
			dropped = true;
		}

		if(dropped)
			timer = delay.getValueI();
	}
}
