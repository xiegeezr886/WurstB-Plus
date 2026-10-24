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

import java.util.function.Predicate;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;

/**
 * Ported from Meteor Client (GPL-3.0).
 *
 * <p>
 * This is a simplified port: it only works while a brewing stand screen is
 * open, it always uses blaze powder as fuel, and it only knows the vanilla
 * potion recipes listed in {@link PotionRecipe}. It does not open the brewing
 * stand for you.
 */
@SearchTags({"auto brewer", "AutoBrew", "auto brew", "BrewingBot",
	"brewing bot", "potion bot"})
public final class AutoBrewerHack extends Hack implements UpdateListener
{
	private static final int BOTTLE_SLOT_START = 0;
	private static final int BOTTLE_SLOT_END = 3;
	private static final int INGREDIENT_SLOT = 3;
	private static final int FUEL_SLOT = 4;
	private static final int INVENTORY_SLOT_START = 5;

	private final EnumSetting<PotionRecipe> potion = new EnumSetting<>(
		"Potion", "The type of potion to brew.", PotionRecipe.values(),
		PotionRecipe.STRENGTH);

	private final SliderSetting delay = new SliderSetting("Delay",
		"How long to wait between brewing actions.", 5, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private int stage = -2;
	private int timer;
	private int lastContainerId = -1;

	public AutoBrewerHack()
	{
		super("AutoBrewer");
		setCategory(Category.ITEMS);
		addSetting(potion);
		addSetting(delay);
	}

	@Override
	protected void onEnable()
	{
		stage = -2;
		timer = 0;
		lastContainerId = -1;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		stage = -2;
		timer = 0;
		lastContainerId = -1;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.gameMode == null)
			return;

		if(!(MC.gui.screen() instanceof AbstractContainerScreen<?> screen))
		{
			lastContainerId = -1;
			return;
		}

		if(!(screen.getMenu() instanceof BrewingStandMenu menu))
			return;

		// restart the loop whenever a different brewing stand is opened
		if(menu.containerId != lastContainerId)
		{
			lastContainerId = menu.containerId;
			stage = -2;
			timer = 0;
		}

		// never interfere with an item held by the cursor
		if(!menu.getCarried().isEmpty())
			return;

		timer++;
		if(timer < delay.getValueI())
			return;

		// wait for the current brew to finish
		if(menu.getBrewingTicks() != 0)
			return;

		timer = 0;

		if(stage == -2)
		{
			if(takePotions(menu))
				return;
			stage = -1;
			return;
		}

		if(stage == -1)
		{
			if(insertWaterBottles(menu))
				return;
			stage = 0;
			return;
		}

		Item[] ingredients = potion.getSelected().ingredients;
		if(stage >= ingredients.length)
		{
			stage = -2;
			return;
		}

		if(checkFuel(menu))
			return;

		// advance immediately so the same ingredient isn't inserted twice
		// once the brew finishes
		boolean clicked = insertIngredient(menu, ingredients[stage]);
		stage++;
		if(clicked)
			return;
	}

	private boolean takePotions(BrewingStandMenu menu)
	{
		for(int i = BOTTLE_SLOT_START; i < BOTTLE_SLOT_END; i++)
		{
			if(menu.getSlot(i).getItem().isEmpty())
				continue;

			quickMove(menu, i);

			if(!menu.getSlot(i).getItem().isEmpty())
			{
				ChatUtils.error(
					"Not enough inventory space. Disabling AutoBrewer.");
				setEnabled(false);
			}

			return true;
		}

		return false;
	}

	private boolean insertWaterBottles(BrewingStandMenu menu)
	{
		for(int i = BOTTLE_SLOT_START; i < BOTTLE_SLOT_END; i++)
		{
			if(!menu.getSlot(i).getItem().isEmpty())
				continue;

			int slot = findItem(menu, this::isWaterBottle);
			if(slot == -1)
			{
				ChatUtils.error("Out of water bottles. Disabling AutoBrewer.");
				setEnabled(false);
				return true;
			}

			moveOne(menu, slot, i);
			return true;
		}

		return false;
	}

	private boolean checkFuel(BrewingStandMenu menu)
	{
		if(menu.getFuel() > 0)
			return false;

		int slot = findItem(menu, stack -> stack.is(Items.BLAZE_POWDER));
		if(slot == -1)
		{
			ChatUtils.error("Out of blaze powder. Disabling AutoBrewer.");
			setEnabled(false);
			return true;
		}

		moveOne(menu, slot, FUEL_SLOT);
		return false;
	}

	private boolean insertIngredient(BrewingStandMenu menu, Item ingredient)
	{
		ItemStack current = menu.getSlot(INGREDIENT_SLOT).getItem();
		if(!current.isEmpty())
			return false;

		int slot = findItem(menu, stack -> stack.is(ingredient));
		if(slot == -1)
		{
			ChatUtils.error("Out of "
				+ BuiltInRegistries.ITEM.getKey(ingredient)
				+ ". Disabling AutoBrewer.");
			setEnabled(false);
			return true;
		}

		moveOne(menu, slot, INGREDIENT_SLOT);
		return true;
	}

	private int findItem(BrewingStandMenu menu, Predicate<ItemStack> predicate)
	{
		for(int i = INVENTORY_SLOT_START; i < menu.slots.size(); i++)
			if(predicate.test(menu.getSlot(i).getItem()))
				return i;

		return -1;
	}

	private boolean isWaterBottle(ItemStack stack)
	{
		if(!stack.is(Items.POTION))
			return false;

		PotionContents contents = stack.getOrDefault(
			DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		return contents.potion().isPresent()
			&& contents.potion().get().value() == Potions.WATER.value();
	}

	private void quickMove(BrewingStandMenu menu, int slot)
	{
		MC.gameMode.handleContainerInput(menu.containerId, slot, 0,
			ContainerInput.QUICK_MOVE, MC.player);
		timer = 0;
	}

	/**
	 * Moves exactly one item from one slot to another, leaving the rest of the
	 * stack in the source slot.
	 */
	private void moveOne(BrewingStandMenu menu, int from, int to)
	{
		MC.gameMode.handleContainerInput(menu.containerId, from, 0,
			ContainerInput.PICKUP, MC.player);
		MC.gameMode.handleContainerInput(menu.containerId, to, 1,
			ContainerInput.PICKUP, MC.player);

		// put the rest of the stack back
		if(!menu.getCarried().isEmpty())
			MC.gameMode.handleContainerInput(menu.containerId, from, 0,
				ContainerInput.PICKUP, MC.player);

		timer = 0;
	}

	private enum PotionRecipe
	{
		STRENGTH("Strength", Items.NETHER_WART, Items.BLAZE_POWDER),
		HEALING("Healing", Items.NETHER_WART,
			Items.GLISTERING_MELON_SLICE),
		REGENERATION("Regeneration", Items.NETHER_WART, Items.GHAST_TEAR),
		SWIFTNESS("Swiftness", Items.NETHER_WART, Items.SUGAR),
		FIRE_RESISTANCE("Fire resistance", Items.NETHER_WART,
			Items.MAGMA_CREAM),
		NIGHT_VISION("Night vision", Items.NETHER_WART,
			Items.GOLDEN_CARROT),
		WATER_BREATHING("Water breathing", Items.NETHER_WART,
			Items.PUFFERFISH),
		POISON("Poison", Items.NETHER_WART, Items.SPIDER_EYE),
		WEAKNESS("Weakness", Items.FERMENTED_SPIDER_EYE),
		SLOWNESS("Slowness", Items.NETHER_WART, Items.SUGAR,
			Items.FERMENTED_SPIDER_EYE);

		private final String name;
		private final Item[] ingredients;

		private PotionRecipe(String name, Item... ingredients)
		{
			this.name = name;
			this.ingredients = ingredients;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
