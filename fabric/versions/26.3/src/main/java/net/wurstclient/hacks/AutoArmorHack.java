/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 * Copyright (c) 2015-2026 CCBlueX
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.wurstclient.util.InventoryUtils;
import net.minecraft.world.entity.player.Inventory;
// ArmorItem removed in MC 26.1.2
// ArmorItem.Type removed in MC 26.1.2
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.util.EnchantmentUtils;
import net.wurstclient.util.MovementPlanner;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"auto armor"})
public final class AutoArmorHack extends Hack
	implements UpdateListener, PacketOutputListener
{
	private final CheckboxSetting useEnchantments = new CheckboxSetting(
		"Use enchantments",
		"Whether or not to consider the Protection enchantment when calculating armor strength.",
		true);
	
	private final CheckboxSetting swapWhileMoving = new CheckboxSetting(
		"Swap while moving",
		"Whether or not to swap armor pieces while the player is moving.\n\n"
			+ "\u00a7c\u00a7lWARNING:\u00a7r This would not be possible without cheats. It may raise suspicion.",
		false);
	
	private final SliderSetting delay = new SliderSetting("Delay",
		"Amount of ticks to wait before swapping the next piece of armor.", 2,
		0, 20, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting antiBreak = new CheckboxSetting("Anti break",
		"Skips armor that is close to breaking.", true);

	private final SliderSetting minDurability = new SliderSetting(
		"Minimum durability", "Armor at or below this durability is skipped.",
		5, 1, 50, 1, ValueDisplay.PERCENTAGE)
			.visibleWhen(antiBreak::isChecked);

	private final CheckboxSetting keepElytra = new CheckboxSetting(
		"Keep elytra", "Does not replace an equipped elytra.", true);
	
	private int timer;
	
	public AutoArmorHack()
	{
		super("AutoArmor");
		setCategory(Category.COMBAT);
		addSetting(useEnchantments);
		addSetting(swapWhileMoving);
		addSetting(delay);
		addSetting(antiBreak);
		addSetting(minDurability);
		addSetting(keepElytra);
	}
	
	@Override
	protected void onEnable()
	{
		timer = 0;
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketOutputListener.class, this);
	}
	
	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketOutputListener.class, this);
		WURST.getInventoryActionQueue().cancel(this);
	}
	
	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.player.isSpectator())
			return;

		// wait for timer
		if(timer > 0)
		{
			timer--;
			return;
		}
		
		// check screen
		if(MC.gui.screen() instanceof AbstractContainerScreen
			&& !(MC.gui.screen() instanceof InventoryScreen))
			return;

		if(WURST.getInventoryActionQueue().hasPending(this))
			return;
		
		LocalPlayer player = MC.player;
		Inventory inventory = player.getInventory();
		
		if(!swapWhileMoving.isChecked()
			&& MovementPlanner.isMoving(player.input))
			return;
		
		// store slots and values of best armor pieces
		int[] bestArmorSlots = new int[4];
		int[] bestArmorValues = new int[4];
		int[] equippedArmorValues = new int[4];
		
		// initialize with currently equipped armor
		// (36 + type maps onto the equipment slots: 0 = feet, 3 = head)
		for(int type = 0; type < 4; type++)
		{
			bestArmorSlots[type] = -1;
			
			ItemStack stack = inventory.getItem(36 + type);
			if(type == 2 && keepElytra.isChecked()
				&& stack.is(Items.ELYTRA))
			{
				bestArmorValues[type] = Integer.MAX_VALUE;
				continue;
			}
			if(stack.isEmpty() || !isArmor(stack))
				continue;
			if(EnchantmentUtils.getLevel(Enchantments.BINDING_CURSE,
				stack) > 0)
			{
				bestArmorValues[type] = Integer.MAX_VALUE;
				continue;
			}
			
			bestArmorValues[type] = getArmorValue(stack);
			equippedArmorValues[type] = bestArmorValues[type];
		}
		
		// search the inventory for better armor
		for(int slot = 0; slot < 36; slot++)
		{
			ItemStack stack = inventory.getItem(slot);
			
			if(stack.isEmpty() || !isArmor(stack))
				continue;
			if(isLowDurability(stack)
				|| EnchantmentUtils.getLevel(Enchantments.BINDING_CURSE,
					stack) > 0)
				continue;
			
			int armorType =
				stack.get(DataComponents.EQUIPPABLE).slot().getIndex();
			int armorValue = getArmorValue(stack);
			
			if(armorValue > bestArmorValues[armorType])
			{
				bestArmorSlots[armorType] = slot;
				bestArmorValues[armorType] = armorValue;
			}
		}
		
		// equip the largest deterministic upgrade first
		ArrayList<Integer> types =
			new ArrayList<>(Arrays.asList(0, 1, 2, 3));
		types.sort(Comparator.<Integer>comparingInt(type ->
			bestArmorValues[type] - equippedArmorValues[type]).reversed());
		for(int type : types)
		{
			// check if better armor was found
			int slot = bestArmorSlots[type];
			if(slot == -1)
				continue;
			
			// swapping needs a free slot for the old armor piece
			ItemStack oldArmor = inventory.getItem(36 + type);
			if(!oldArmor.isEmpty() && inventory.getFreeSlot() == -1)
				continue;
			
			int inventorySlot = slot;
			ItemStack expectedArmor =
				inventory.getItem(inventorySlot).copy();
			
			// menu slot ids: armor is 5..8 (head..feet), hotbar is +36
			int sourceSlot = InventoryUtils.toNetworkSlot(slot);
			int armorSlot = InventoryUtils.toNetworkSlot(36 + type);
			boolean hasOldArmor = !oldArmor.isEmpty();
			WURST.getInventoryActionQueue().submit(this, 50,
				() -> MC.player != null
					&& MC.player.containerMenu.containerId == 0
					&& ItemStack.isSameItemSameComponents(expectedArmor,
						MC.player.getInventory().getItem(inventorySlot)),
				hasOldArmor
					? new Runnable[]{() -> IMC.getInteractionManager()
						.windowClick_QUICK_MOVE(armorSlot),
						() -> IMC.getInteractionManager()
							.windowClick_QUICK_MOVE(sourceSlot)}
					: new Runnable[]{() -> IMC.getInteractionManager()
						.windowClick_QUICK_MOVE(sourceSlot)});
			
			break;
		}
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(event.getPacket() instanceof ServerboundContainerClickPacket)
			timer = delay.getValueI();
	}
	
	/**
	 * 1.21.5 removed ArmorItem; a piece of armour is anything whose
	 * EQUIPPABLE component reports a humanoid armour slot.
	 */
	private static boolean isArmor(ItemStack stack)
	{
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot().getType()
			.equals(EquipmentSlot.Type.HUMANOID_ARMOR);
	}

	private int getArmorValue(ItemStack stack)
	{
		EquipmentSlot slot = stack.get(DataComponents.EQUIPPABLE).slot();
		ItemAttributeModifiers mods =
			stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
				ItemAttributeModifiers.EMPTY);
		int armorPoints = (int)mods.compute(Attributes.ARMOR, 0, slot);
		int prtPoints = 0;
		int armorToughness = 0;
		int durabilityScore = stack.isDamageableItem()
			? (stack.getMaxDamage() - stack.getDamageValue()) * 5
				/ Math.max(1, stack.getMaxDamage())
			: 5;
		
		if(useEnchantments.isChecked())
			prtPoints = EnchantmentUtils.getLevel(Enchantments.PROTECTION,
				stack);
		
		return armorPoints * 5 + prtPoints * 3 + armorToughness
			+ durabilityScore;
	}

	private boolean isLowDurability(ItemStack stack)
	{
		if(!antiBreak.isChecked() || !stack.isDamageableItem())
			return false;

		int remaining = stack.getMaxDamage() - stack.getDamageValue();
		return remaining * 100 <= stack.getMaxDamage()
			* minDurability.getValueI();
	}
}
