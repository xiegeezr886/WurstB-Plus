/*
 * Copyright (c) 2025 LeavesHack contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from LeavesHack (AGPL-3.0).
package net.wurstclient.hacks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EnchantmentUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.text.WText;

@SearchTags({"auto armor plus", "auto armor", "elytra", "AutoArmorPlus"})
public final class AutoArmorPlusHack extends Hack implements UpdateListener
{
	private static final int ELYTRA_SCORE = 1;

	private final SliderSetting delay = new SliderSetting("Delay",
		WText.literal("Ticks to wait between armor swaps."), 2, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting useEnchantments = new CheckboxSetting(
		"Use enchantments",
		WText.literal("Counts Protection when comparing armor pieces."), true);

	private final CheckboxSetting autoElytra = new CheckboxSetting(
		"Auto elytra",
		WText.literal("Equips a working elytra while ElytraFly or ExtraElytra"
			+ " is enabled."),
		true);

	private final CheckboxSetting keepElytra = new CheckboxSetting(
		"Keep elytra", WText.literal("Doesn't replace an equipped elytra."),
		true);

	private final CheckboxSetting antiBreak = new CheckboxSetting(
		"Anti break", WText.literal("Skips armor that is close to breaking."),
		true);

	private final SliderSetting minDurability = new SliderSetting(
		"Minimum durability",
		WText.literal("Armor at or below this durability is skipped."), 5, 1, 50,
		1, ValueDisplay.PERCENTAGE);

	private int timer;

	public AutoArmorPlusHack()
	{
		super("AutoArmorPlus");
		setCategory(Category.COMBAT);
		addSetting(delay);
		addSetting(useEnchantments);
		addSetting(autoElytra);
		addSetting(keepElytra);
		addSetting(antiBreak);
		addSetting(minDurability);
	}

	@Override
	protected void onEnable()
	{
		timer = 0;
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
		if(MC.player == null || MC.player.isSpectator())
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		if(MC.screen instanceof AbstractContainerScreen
			&& !(MC.screen instanceof InventoryScreen))
			return;

		if(MC.player.containerMenu.containerId != 0)
			return;

		Inventory inventory = MC.player.getInventory();
		boolean elytraMode =
			autoElytra.isChecked() && (WURST.getHax().elytraFlyHack.isEnabled()
				|| WURST.getHax().extraElytraHack.isEnabled());

		int[] bestSlots = {-1, -1, -1, -1};
		int[] bestValues = new int[4];
		int[] equippedValues = new int[4];

		for(int type = 0; type < 4; type++)
		{
			ItemStack stack = inventory.getItem(36 + type);

			if(type == 2 && elytraMode)
			{
				bestValues[2] = isUsableElytra(stack) ? Integer.MAX_VALUE : 0;
				continue;
			}

			if(type == 2 && keepElytra.isChecked() && stack.is(Items.ELYTRA))
			{
				bestValues[2] = Integer.MAX_VALUE;
				continue;
			}

			if(stack.isEmpty() || !isArmor(stack))
				continue;

			if(EnchantmentUtils.getLevel(Enchantments.BINDING_CURSE,
				stack) > 0)
			{
				bestValues[type] = Integer.MAX_VALUE;
				continue;
			}

			bestValues[type] = getArmorValue(stack);
			equippedValues[type] = bestValues[type];
		}

		for(int slot = 0; slot < 36; slot++)
		{
			ItemStack stack = inventory.getItem(slot);
			if(stack.isEmpty())
				continue;

			if(stack.is(Items.ELYTRA))
			{
				if(elytraMode && isUsableElytra(stack)
					&& bestValues[2] != Integer.MAX_VALUE)
				{
					bestSlots[2] = slot;
					bestValues[2] = ELYTRA_SCORE;
				}
				continue;
			}

			if(!isArmor(stack) || isLowDurability(stack))
				continue;

			if(EnchantmentUtils.getLevel(Enchantments.BINDING_CURSE,
				stack) > 0)
				continue;

			int type = stack.get(DataComponents.EQUIPPABLE).slot().getIndex();
			if(elytraMode && type == 2)
				continue;

			int value = getArmorValue(stack);
			if(value > bestValues[type])
			{
				bestSlots[type] = slot;
				bestValues[type] = value;
			}
		}

		ArrayList<Integer> types =
			new ArrayList<>(Arrays.asList(0, 1, 2, 3));
		types.sort(Comparator
			.<Integer> comparingInt(
				type -> bestValues[type] - equippedValues[type])
			.reversed());

		for(int type : types)
		{
			int slot = bestSlots[type];
			if(slot == -1)
				continue;

			ItemStack oldArmor = inventory.getItem(36 + type);
			if(!oldArmor.isEmpty() && inventory.getFreeSlot() == -1)
				continue;

			int sourceSlot = InventoryUtils.toNetworkSlot(slot);
			int armorSlot = InventoryUtils.toNetworkSlot(36 + type);

			if(oldArmor.isEmpty())
			{
				IMC.getInteractionManager().windowClick_QUICK_MOVE(sourceSlot);
			}else
			{
				IMC.getInteractionManager().windowClick_QUICK_MOVE(armorSlot);
				IMC.getInteractionManager().windowClick_QUICK_MOVE(sourceSlot);
			}

			timer = delay.getValueI();
			return;
		}
	}

	private boolean isArmor(ItemStack stack)
	{
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot().getType()
			.equals(EquipmentSlot.Type.HUMANOID_ARMOR);
	}

	private boolean isUsableElytra(ItemStack stack)
	{
		return stack.is(Items.ELYTRA) && (!stack.isDamageableItem()
			|| stack.getDamageValue() < stack.getMaxDamage());
	}

	private boolean isLowDurability(ItemStack stack)
	{
		if(!antiBreak.isChecked() || !stack.isDamageableItem())
			return false;

		int remaining = stack.getMaxDamage() - stack.getDamageValue();
		return remaining * 100 <= stack.getMaxDamage()
			* minDurability.getValueI();
	}

	private int getArmorValue(ItemStack stack)
	{
		EquipmentSlot slot = stack.get(DataComponents.EQUIPPABLE).slot();
		ItemAttributeModifiers mods = stack.getOrDefault(
			DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		int armorPoints = (int)mods.compute(Attributes.ARMOR, 0, slot);
		int prtPoints = useEnchantments.isChecked()
			? EnchantmentUtils.getLevel(Enchantments.PROTECTION, stack) : 0;
		int durabilityScore = stack.isDamageableItem()
			? (stack.getMaxDamage() - stack.getDamageValue()) * 5
				/ Math.max(1, stack.getMaxDamage())
			: 5;

		return armorPoints * 5 + prtPoints * 3 + durabilityScore;
	}
}
