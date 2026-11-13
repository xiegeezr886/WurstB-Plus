/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.ResourceLocationException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public enum ItemUtils
{
	;
	
	/**
	 * @param nameOrId
	 *            a String containing the item's name ({@link ResourceLocation}) or
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
			return BuiltInRegistries.ITEM.getOptional(new ResourceLocation(nameOrId))
				.orElse(null);
			
		}catch(ResourceLocationException e)
		{
			return null;
		}
	}
	
	public static float getAttackSpeed(Item item)
	{
		return (float)item.getDefaultAttributeModifiers(EquipmentSlot.MAINHAND)
			.get(Attributes.ATTACK_SPEED).stream().findFirst()
			.orElseThrow().getAmount();
	}
	
	/**
	 * Adds the specified enchantment to the specified item stack. Unlike
	 * {@link ItemStack#enchant(Enchantment, int)}, this method doesn't
	 * limit the level to 127.
	 */
	public static void addEnchantment(ItemStack stack, Enchantment enchantment,
		int level)
	{
		ResourceLocation id = EnchantmentHelper.getEnchantmentId(enchantment);
		ListTag nbt = getOrCreateNbtList(stack, ItemStack.TAG_ENCH);
		nbt.add(EnchantmentHelper.storeEnchantment(id, level));
	}
	
	public static ListTag getOrCreateNbtList(ItemStack stack, String key)
	{
		CompoundTag nbt = stack.getOrCreateTag();
		if(!nbt.contains(key, Tag.TAG_LIST))
			nbt.put(key, new ListTag());
		
		return nbt.getList(key, Tag.TAG_COMPOUND);
	}
}
