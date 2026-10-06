/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autolibrarian;

import java.util.Objects;
import net.minecraft.ResourceLocationException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.wurstclient.WurstClient;
import net.wurstclient.WurstTranslator;

public record BookOffer(String id, int level, int price)
	implements Comparable<BookOffer>
{
	public static BookOffer create(Enchantment enchantment)
	{
		ResourceLocation id = BuiltInRegistries.ENCHANTMENT.getKey(enchantment);
		return new BookOffer("" + id, enchantment.getMaxLevel(), 64);
	}
	
	public Enchantment getEnchantment()
	{
		// id 来自设置文件：可能是 null（缺少 id 字段），也可能格式非法。
		// new ResourceLocation(null) 抛的是 NPE，catch(ResourceLocationException) 接不住，所以先判 null。
		if(id == null)
			return null;

		try
		{
			return BuiltInRegistries.ENCHANTMENT.get(new ResourceLocation(id));

		}catch(ResourceLocationException e)
		{
			// 非法 id 以前是抛出去的：于是 isValid() 不是返回 false 而是炸掉，
			// 而它正是在构建添加界面列表时被调的 —— 界面还没开出来就崩。
			return null;
		}
	}
	
	public String getEnchantmentName()
	{
		WurstTranslator translator = WurstClient.INSTANCE.getTranslator();
		Enchantment enchantment = getEnchantment();

		// 未知附魔：退回显示原始 id，而不是在这里 NPE
		if(enchantment == null)
			return id;

		return translator.translateMcEnglish(enchantment.getDescriptionId());
	}
	
	public String getEnchantmentNameWithLevel()
	{
		WurstTranslator translator = WurstClient.INSTANCE.getTranslator();
		Enchantment enchantment = getEnchantment();

		if(enchantment == null)
			return id;

		String name =
			translator.translateMcEnglish(enchantment.getDescriptionId());
		
		if(enchantment.getMaxLevel() > 1)
			name += " "
				+ translator.translateMcEnglish("enchantment.level." + level);
		
		return name;
	}
	
	public String getFormattedPrice()
	{
		return price + " emerald" + (price == 1 ? "" : "s");
	}
	
	public boolean isValid()
	{
		Enchantment enchantment = getEnchantment();
		return enchantment != null
			&& enchantment.isTradeable() && level >= 1
			&& level <= enchantment.getMaxLevel() && price >= 1 && price <= 64;
	}
	
	@Override
	public int compareTo(BookOffer other)
	{
		int idCompare = id.compareTo(other.id);
		if(idCompare != 0)
			return idCompare;
		
		return Integer.compare(level, other.level);
	}
	
	@Override
	public boolean equals(Object obj)
	{
		if(this == obj)
			return true;
		
		if(obj == null || getClass() != obj.getClass())
			return false;
		
		BookOffer other = (BookOffer)obj;
		return id.equals(other.id) && level == other.level;
	}
	
	@Override
	public int hashCode()
	{
		return Objects.hash(id, level);
	}
}
