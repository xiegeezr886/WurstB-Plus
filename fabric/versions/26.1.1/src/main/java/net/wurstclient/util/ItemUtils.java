/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.IdentifierException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public enum ItemUtils
{
	;
	
	/**
	 * @param nameOrId
	 *            a String containing the item's name ({@link Identifier}) or
	 *            numeric ID.
	 * @return the requested item, or null if the item doesn't exist.
	 */
	public static Item getItemFromNameOrID(String nameOrId)
	{
		if(MathUtils.isInteger(nameOrId))
		{
			// There is no getOrEmpty() for raw IDs, so this detects when the
			// Registry defaults and returns null instead
			int id = Integer.parseInt(nameOrId);
			Item item = BuiltInRegistries.ITEM.byId(id);
			if(id != 0 && BuiltInRegistries.ITEM.getId(item) == 0)
				return null;
			
			return item;
		}
		
		try
		{
			return BuiltInRegistries.ITEM.getOptional(Identifier.parse(nameOrId))
				.orElse(null);
			
		}catch(IdentifierException e)
		{
			return null;
		}
	}
	
	public static float getAttackSpeed(Item item)
	{
		float[] speed = {0};
		item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
			ItemAttributeModifiers.EMPTY).forEach(EquipmentSlot.MAINHAND,
			(attribute, modifier) -> {
				if(attribute.equals(Attributes.ATTACK_SPEED))
					speed[0] += (float)modifier.amount();
			});
		return speed[0];
	}
	
	/**
	 * Adds the specified enchantment to the specified item stack. Unlike
	 * {@link ItemStack#enchant(Holder, int)}, this method doesn't
	 * limit the level to 127.
	 */
	public static void addEnchantment(ItemStack stack,
		Holder<Enchantment> enchantment,
		int level)
	{
		EnchantmentHelper.updateEnchantments(stack,
			enchantments -> enchantments.set(enchantment, level));
	}
}
