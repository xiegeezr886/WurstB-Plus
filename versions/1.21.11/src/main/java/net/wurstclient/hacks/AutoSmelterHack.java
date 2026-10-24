/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipePropertySet;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Simplified port of Meteor's AutoSmelter module.
 *
 * <p>
 * Limitation: this only works while a furnace, blast furnace or smoker screen
 * is open. It shift-clicks whole stacks into empty input and fuel slots, so it
 * cannot top up partially filled slots and does not move exact fuel amounts.
 */
@SearchTags({"auto smelter", "AutoSmelt", "auto smelt", "furnace bot",
	"FurnaceBot", "smelting"})
public final class AutoSmelterHack extends Hack implements UpdateListener
{
	private final ItemListSetting fuelItems = new ItemListSetting(
		"Fuel items", "Items that may be used as fuel.", "minecraft:coal",
		"minecraft:coal_block", "minecraft:charcoal", "minecraft:blaze_rod");

	private final ItemListSetting smeltableItems = new ItemListSetting(
		"Smeltable items", "Items that may be put into the input slot.",
		"minecraft:copper_ore", "minecraft:gold_ore", "minecraft:iron_ore",
		"minecraft:raw_copper", "minecraft:raw_gold", "minecraft:raw_iron");

	private final SliderSetting delay = new SliderSetting("Delay",
		"Delay between moving stacks of items.", 4, 1, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting takeOutput = new CheckboxSetting(
		"Take output", "Moves smelted items from the result slot into your"
			+ " inventory.",
		true);

	private final CheckboxSetting insertFuel = new CheckboxSetting(
		"Insert fuel", "Puts fuel into an empty fuel slot when the furnace"
			+ " isn't lit.",
		true);

	private final CheckboxSetting insertItems = new CheckboxSetting(
		"Insert items", "Puts smeltable items into an empty input slot.", true);

	private final CheckboxSetting autoClose = new CheckboxSetting("Auto close",
		"Closes the furnace screen once there is nothing left to do.", false);

	private int timer;

	public AutoSmelterHack()
	{
		super("AutoSmelter");
		setCategory(Category.ITEMS);
		addSetting(fuelItems);
		addSetting(smeltableItems);
		addSetting(delay);
		addSetting(takeOutput);
		addSetting(insertFuel);
		addSetting(insertItems);
		addSetting(autoClose);
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
		timer = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;
		if(!(MC.screen instanceof AbstractContainerScreen<?> screen))
			return;
		if(!(screen.getMenu() instanceof AbstractFurnaceMenu menu))
			return;

		// Never interfere with an item that is currently held by the cursor.
		if(!menu.getCarried().isEmpty())
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		ItemStack result =
			menu.getSlot(AbstractFurnaceMenu.RESULT_SLOT).getItem();
		if(takeOutput.isChecked() && !result.isEmpty())
		{
			quickMove(menu, AbstractFurnaceMenu.RESULT_SLOT);
			return;
		}

		ItemStack fuel = menu.getSlot(AbstractFurnaceMenu.FUEL_SLOT).getItem();
		if(insertFuel.isChecked() && !menu.isLit() && fuel.isEmpty())
		{
			int slot = findItem(menu, true);
			if(slot >= 0)
			{
				quickMove(menu, slot);
				return;
			}
		}

		ItemStack input =
			menu.getSlot(AbstractFurnaceMenu.INGREDIENT_SLOT).getItem();
		if(insertItems.isChecked() && input.isEmpty())
		{
			int slot = findItem(menu, false);
			if(slot >= 0)
			{
				quickMove(menu, slot);
				return;
			}
		}

		if(autoClose.isChecked())
			MC.setScreen(null);
	}

	private int findItem(AbstractFurnaceMenu menu, boolean fuel)
	{
		for(int i = AbstractFurnaceMenu.SLOT_COUNT; i < menu.slots
			.size(); i++)
		{
			ItemStack stack = menu.getSlot(i).getItem();
			if(stack.isEmpty())
				continue;

			if(fuel)
			{
				if(!fuelItems.getItemNames().contains(
					BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
					continue;
				if(!MC.level.fuelValues().isFuel(stack))
					continue;
			}else
			{
				if(!smeltableItems.getItemNames().contains(
					BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
					continue;
				if(!canSmelt(menu, stack))
					continue;
			}

			return i;
		}

		return -1;
	}

	private boolean canSmelt(AbstractFurnaceMenu menu, ItemStack stack)
	{
		if(menu instanceof BlastFurnaceMenu)
			return MC.level.recipeAccess()
				.propertySet(RecipePropertySet.BLAST_FURNACE_INPUT)
				.test(stack);
		if(menu instanceof SmokerMenu)
			return MC.level.recipeAccess()
				.propertySet(RecipePropertySet.SMOKER_INPUT).test(stack);

		return MC.level.recipeAccess()
			.propertySet(RecipePropertySet.FURNACE_INPUT).test(stack);
	}

	private void quickMove(AbstractFurnaceMenu menu, int slot)
	{
		MC.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0,
			ClickType.QUICK_MOVE, MC.player);
		timer = delay.getValueI();
	}
}
