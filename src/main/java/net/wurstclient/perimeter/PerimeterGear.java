/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.perimeter.config.PerimeterAdvancedConfig;

/**
 * Ported from the reference mod: watches the durability of the tools and the
 * Elytra, decides when a damaged item has to be swapped for a healthy one or
 * taken to a repair facility, and matches items that are worth keeping when the
 * products are unloaded.
 */
public final class PerimeterGear
{
	public static final int OFFHAND_MENU_SLOT = 45;
	public static final int CHEST_MENU_SLOT = 6;
	
	private PerimeterGear()
	{}
	
	public record Slot(int inventorySlot, int menuSlot, ItemStack stack,
		boolean offhand, boolean chest)
	{
		public boolean isEmpty()
		{
			return stack == null || stack.isEmpty();
		}
		
		public Item item()
		{
			return stack.getItem();
		}
	}
	
	public record Replacement(Slot low, Slot healthy)
	{}
	
	public record Check(boolean repairRequired, Item repairItem,
		Replacement replacement)
	{
		public static final Check NONE = new Check(false, null, null);
	}
	
	public static int remainingDurability(ItemStack stack)
	{
		if(stack == null || !stack.isDamageableItem())
			return Integer.MAX_VALUE;
		
		return stack.getMaxDamage() - stack.getDamageValue();
	}
	
	public static int threshold(ItemStack stack, PerimeterAdvancedConfig config)
	{
		if(stack.is(Items.ELYTRA))
			return config.elytraDurabilityThreshold;
		
		if(stack.getItem() instanceof DiggerItem)
			return config.toolDurabilityThreshold;
		
		return config.toolDurabilityThreshold;
	}
	
	/**
	 * The reference inspects tools first and the Elytra second, and reports a
	 * repair request as soon as something is below its threshold.
	 */
	public static Check inspect(Player player, boolean elytraEnabled,
		PerimeterAdvancedConfig config)
	{
		if(player == null)
			return Check.NONE;
		
		List<Slot> tools = new ArrayList<>();
		List<Slot> elytras = new ArrayList<>();
		Inventory inventory = player.getInventory();
		
		for(int slot = 0; slot < PerimeterInventoryPolicy.INVENTORY_SLOTS; slot++)
			classify(new Slot(slot, menuSlot(slot), inventory.getItem(slot),
				false, false), tools, elytras, elytraEnabled);
		
		classify(new Slot(PerimeterInventoryPolicy.OFFHAND_SLOT,
			OFFHAND_MENU_SLOT, inventory.getItem(PerimeterInventoryPolicy
				.OFFHAND_SLOT), true, false), tools, elytras, elytraEnabled);
		
		classify(new Slot(-1, CHEST_MENU_SLOT,
			player.getItemBySlot(EquipmentSlot.CHEST), false, true), tools,
			elytras, elytraEnabled);
		
		Check toolsCheck = inspectGroup(tools, config);
		
		if(toolsCheck.repairRequired() || toolsCheck.replacement() != null)
			return toolsCheck;
		
		return inspectGroup(elytras, config);
	}
	
	private static void classify(Slot slot, List<Slot> tools, List<Slot> elytras,
		boolean elytraEnabled)
	{
		if(slot.isEmpty() || !slot.stack().isDamageableItem())
			return;
		
		if(slot.stack().is(Items.ELYTRA))
		{
			if(elytraEnabled)
				elytras.add(slot);
			return;
		}
		
		if(slot.stack().getItem() instanceof DiggerItem)
			tools.add(slot);
	}
	
	private static Check inspectGroup(List<Slot> group,
		PerimeterAdvancedConfig config)
	{
		for(Slot low : group)
		{
			int remaining = remainingDurability(low.stack());
			
			if(remaining > threshold(low.stack(), config))
				continue;
			
			Slot healthy = findHealthy(group, low);
			return new Check(true, low.item(),
				healthy == null ? null : new Replacement(low, healthy));
		}
		
		return Check.NONE;
	}
	
	private static Slot findHealthy(List<Slot> group, Slot low)
	{
		Slot best = null;
		int bestRemaining = remainingDurability(low.stack());
		
		for(Slot candidate : group)
		{
			if(candidate == low || candidate.chest()
				|| !candidate.item().equals(low.item()))
				continue;
			
			int remaining = remainingDurability(candidate.stack());
			
			if(remaining <= bestRemaining)
				continue;
			
			best = candidate;
			bestRemaining = remaining;
		}
		
		return best;
	}
	
	/**
	 * The parts of a stack the unloading rules depend on, as the reference
	 * defines them: anything damageable, wearable, edible, an Elytra, a
	 * firework, or whitelisted is kept.
	 */
	public static boolean keepWhenUnloading(ItemStack stack, Set<Item> whitelist)
	{
		if(stack == null || stack.isEmpty())
			return true;
		
		Item item = stack.getItem();
		
		return stack.isDamageableItem() || stack.is(Items.ELYTRA)
			|| stack.is(Items.FIREWORK_ROCKET) || isTool(stack)
			|| isEquippable(item) || stack.isEdible()
			|| whitelist.contains(item);
	}
	
	public static boolean isTool(ItemStack stack)
	{
		return stack.getItem() instanceof DiggerItem
			|| stack.getMaxStackSize() == 1 && stack.isDamageableItem();
	}
	
	public static boolean isEquippable(Item item)
	{
		return item instanceof ArmorItem || item instanceof ElytraItem;
	}
	
	/**
	 * Maps an inventory slot to the slot number used by container menus.
	 */
	public static int menuSlot(int inventorySlot)
	{
		if(inventorySlot >= PerimeterInventoryPolicy.INVENTORY_SLOTS)
			return inventorySlot;
		
		return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
	}
}
