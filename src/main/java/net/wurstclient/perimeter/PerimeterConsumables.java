/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.util.Locale;
import java.util.Set;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.perimeter.config.PerimeterAdvancedConfig;

/**
 * Ported from the reference mod: keeps the player fed, and restocks the food
 * and fireworks from the consumable supply point.
 */
public final class PerimeterConsumables
{
	private PerimeterConsumables()
	{}
	
	public static int countFood(Player player, Set<Item> foods)
	{
		if(player == null)
			return 0;
		
		Inventory inventory = player.getInventory();
		int count = 0;
		
		for(int slot = 0; slot < PerimeterInventoryPolicy.INVENTORY_SLOTS; slot++)
		{
			ItemStack stack = inventory.getItem(slot);
			
			if(foods.contains(stack.getItem()))
				count += stack.getCount();
		}
		
		return count;
	}
	
	public static int countFireworks(Player player)
	{
		if(player == null)
			return 0;
		
		Inventory inventory = player.getInventory();
		int count = 0;
		
		for(int slot = 0; slot < PerimeterInventoryPolicy.INVENTORY_SLOTS; slot++)
		{
			ItemStack stack = inventory.getItem(slot);
			
			if(stack.is(Items.FIREWORK_ROCKET))
				count += stack.getCount();
		}
		
		return count;
	}
	
	public static int findFoodSlot(Player player, Set<Item> foods)
	{
		if(player == null)
			return -1;
		
		Inventory inventory = player.getInventory();
		
		for(int slot = 0; slot < PerimeterInventoryPolicy.INVENTORY_SLOTS; slot++)
			if(foods.contains(inventory.getItem(slot).getItem()))
				return slot;
		
		return -1;
	}
	
	/**
	 * Ported from the reference: eat when the food level is at or below the
	 * threshold, or when health is low and the bar is not full yet.
	 */
	public static boolean shouldEat(Player player,
		PerimeterAdvancedConfig config)
	{
		if(player == null)
			return false;
		
		int foodLevel = player.getFoodData().getFoodLevel();
		
		return foodLevel <= config.foodLevelThreshold
			|| foodLevel < 20
				&& player.getHealth() <= config.healthEatingThreshold;
	}
	
	public static boolean needsFoodResupply(int foodCount,
		PerimeterAdvancedConfig config)
	{
		return foodCount <= config.foodResupplyTrigger;
	}
	
	public static boolean needsFireworkResupply(int fireworkCount,
		PerimeterAdvancedConfig config)
	{
		return fireworkCount <= config.fireworkResupplyTrigger;
	}
	
	public static int foodShortage(int foodCount,
		PerimeterAdvancedConfig config)
	{
		return Math.max(0, config.foodResupplyTarget - foodCount);
	}
	
	public static int fireworkShortage(int fireworkCount,
		PerimeterAdvancedConfig config)
	{
		return Math.max(0, config.fireworkResupplyTarget - fireworkCount);
	}
	
	public static String describeResupply(int foodCount, int fireworkCount,
		PerimeterAdvancedConfig config)
	{
		return String.format(Locale.ROOT, "food %d/%d, fireworks %d/%d",
			foodCount, config.foodResupplyTarget, fireworkCount,
			config.fireworkResupplyTarget);
	}
}
