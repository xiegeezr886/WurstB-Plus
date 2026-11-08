/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * SPDX-License-Identifier: GPL-3.0
 * Ported from FO (CC0-1.0): blacklist/whitelist and exact-level book rules.
 */
package net.wurstclient.hacks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hacks.autolibrarian.BookOffer;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.EnchantmentRules;
import net.wurstclient.util.InventoryUtils;

/** Drops configured items from the main inventory, preserving the selected slot. */
@SearchTags({"auto trash", "AutoDrop", "auto drop", "trash", "item filter",
	"inventory cleaner", "InvCleaner"})
public final class AutoTrashHack extends Hack implements UpdateListener
{
	private final ItemListSetting items = new ItemListSetting("Items",
		"Items to drop in Blacklist mode or retain in Whitelist mode.", "minecraft:poisonous_potato",
		"minecraft:rotten_flesh", "minecraft:spider_eye",
		"minecraft:stick", "minecraft:wheat_seeds");
	private final ItemListSetting keepItems = new ItemListSetting("Keep items",
		"Items that will never be dropped. Takes priority over all other filters.");
	private final SliderSetting delay = new SliderSetting("Delay",
		"How often the inventory is scanned.", 20, 0, 200, 5,
		ValueDisplay.INTEGER.withSuffix(" ticks"));
	private final EnumSetting<ListMode> mode = new EnumSetting<>("List mode",
		"Blacklist drops matching items. Whitelist keeps matching items.",
		ListMode.values(), ListMode.BLACKLIST);
	private final TextFieldSetting bookRules = new TextFieldSetting("Enchanted book rules",
		"Exact levels, separated by commas. Example: mending=1, swift_sneak=3."
			+ " A book matching any rule counts as listed. Empty disables book rules."
			+ " Invalid or unavailable enchantments pause all dropping.", "");
	private final CheckboxSetting dropAll = new CheckboxSetting("Drop whole stacks",
		"Drops the entire stack. Disable to drop one item from each matching slot.", true);
	private final CheckboxSetting excludeHotbar = new CheckboxSetting("Exclude hotbar",
		"Preserves all hotbar slots. The selected slot is always preserved.", false);
	private final CheckboxSetting toggleOffOnClear = new CheckboxSetting("Disable when clear",
		"Disables the module when no eligible items remain to drop.", false);
	private final CheckboxSetting pauseInScreens = new CheckboxSetting("Pause in screens",
		"Pauses in every screen. Other containers always pause dropping.", false);
	private int timer;
	private String lastInvalidRules;

	public AutoTrashHack()
	{
		super("AutoTrash");
		setCategory(Category.ITEMS);
		addSetting(items);
		addSetting(keepItems);
		addSetting(delay);
		addSetting(mode);
		addSetting(bookRules);
		addSetting(dropAll);
		addSetting(excludeHotbar);
		addSetting(toggleOffOnClear);
		addSetting(pauseInScreens);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		timer = 0;
		lastInvalidRules = null;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(!isEnabled() || MC.player == null || MC.level == null || MC.gameMode == null)
			return;
		// Screen visibility alone cannot establish which container owns slot IDs.
		if(MC.player.containerMenu != MC.player.inventoryMenu
			|| MC.screen instanceof AbstractContainerScreen && !(MC.screen instanceof InventoryScreen)
			|| pauseInScreens.isChecked() && MC.screen != null)
			return;
		if(timer > 0)
		{
			timer--;
			return;
		}

		Map<String, Integer> rules;
		try
		{
			rules = EnchantmentRules.parse(bookRules.getValue());
			for(var rule : rules.entrySet())
				if(new BookOffer(rule.getKey(), rule.getValue(), 64).getEnchantment() == null)
					throw new IllegalArgumentException("Unknown enchantment: " + rule.getKey());
			lastInvalidRules = null;
		}catch(IllegalArgumentException e)
		{
			if(!bookRules.getValue().equals(lastInvalidRules))
				ChatUtils.error("AutoTrash paused: " + e.getMessage());
			lastInvalidRules = bookRules.getValue();
			return;
		}
		List<String> filter = items.getItemNames();
		if(filter.isEmpty() && rules.isEmpty())
			return;
		List<String> keep = keepItems.getItemNames();
		int selectedSlot = MC.player.getInventory().selected;
		boolean dropped = false;
		for(int slot = excludeHotbar.isChecked() ? 9 : 0; slot < 36; slot++)
		{
			if(slot == selectedSlot)
				continue;
			ItemStack stack = MC.player.getInventory().getItem(slot);
			if(stack.isEmpty())
				continue;
			String itemName = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
			if(keep.contains(itemName))
				continue;
			boolean listed = filter.contains(itemName);
			if(stack.is(Items.ENCHANTED_BOOK) && !rules.isEmpty())
				listed |= EnchantmentRules.matches(rules, bookEnchantments(stack));
			else if(filter.isEmpty())
				continue;
			if(mode.getSelected() == ListMode.BLACKLIST ? !listed : listed)
				continue;
			MC.gameMode.handleInventoryMouseClick(MC.player.inventoryMenu.containerId,
				InventoryUtils.toNetworkSlot(slot), dropAll.isChecked() ? 1 : 0,
				ClickType.THROW, MC.player);
			dropped = true;
		}
		if(dropped)
			timer = delay.getValueI();
		else if(toggleOffOnClear.isChecked())
			setEnabled(false);
	}

	private Map<String, Integer> bookEnchantments(ItemStack stack)
	{
		Map<String, Integer> result = new HashMap<>();
		for(var entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet())
			entry.getKey().unwrapKey().ifPresent(key ->
				result.put(key.location().toString(), entry.getIntValue()));
		return result;
	}

	private enum ListMode {BLACKLIST, WHITELIST}
}
