/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.autolibrarian;

import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.enchantment.Enchantment;
import net.wurstclient.WurstClient;
import net.wurstclient.WurstTranslator;
import net.wurstclient.util.EnchantmentUtils;

public record BookOffer(String id, int level, int price)
	implements Comparable<BookOffer>
{
	public static BookOffer create(Holder<Enchantment> enchantment)
	{
		Identifier id = Identifier.parse(enchantment.unwrapKey().orElseThrow()
			.toString());
		return new BookOffer("" + id, enchantment.value().getMaxLevel(), 64);
	}
	
	public static BookOffer createDefault(String id)
	{
		int maxLevel = switch(Identifier.parse(id).getPath())
		{
			case "depth_strider", "fortune", "looting", "respiration" -> 3;
			case "feather_falling", "protection" -> 4;
			case "efficiency", "sharpness" -> 5;
			case "unbreaking" -> 3;
			default -> 1;
		};
		return new BookOffer(id, maxLevel, 64);
	}

	public Holder<Enchantment> getEnchantment()
	{
		return EnchantmentUtils.getHolder(Identifier.parse(id)).orElse(null);
	}
	
	public String getEnchantmentName()
	{
		WurstTranslator translator = WurstClient.INSTANCE.getTranslator();
		Holder<Enchantment> enchantment = getEnchantment();
		return enchantment == null ? id
			: translator.translateMcEnglish(
				enchantment.value().description().getString());
	}
	
	public String getEnchantmentNameWithLevel()
	{
		WurstTranslator translator = WurstClient.INSTANCE.getTranslator();
		Holder<Enchantment> enchantment = getEnchantment();
		if(enchantment == null)
			return id;
		String name =
			translator.translateMcEnglish(enchantment.value().description().getString());
		
		if(enchantment.value().getMaxLevel() > 1)
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
		Holder<Enchantment> enchantment = getEnchantment();
		if(enchantment == null)
			return Identifier.tryParse(id) != null && level >= 1
				&& level <= 255 && price >= 1 && price <= 64;

		return enchantment.is(EnchantmentTags.TRADEABLE) && level >= 1
			&& level <= enchantment.value().getMaxLevel() && price >= 1
			&& price <= 64;
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
