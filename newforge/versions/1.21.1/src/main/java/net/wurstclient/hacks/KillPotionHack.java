/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"kill potion", "KillerPotion", "killer potion", "KillingPotion",
	"killing potion", "InstantDeathPotion", "instant death potion"})
public final class KillPotionHack extends Hack
{
	private final EnumSetting<PotionType> potionType =
		new EnumSetting<>("Potion type", "The type of potion to generate.",
			PotionType.values(), PotionType.SPLASH);
	
	public KillPotionHack()
	{
		super("KillPotion");
		
		setCategory(Category.ITEMS);
		addSetting(potionType);
	}
	
	@Override
	protected void onEnable()
	{
		// check gamemode
		if(!MC.player.getAbilities().instabuild)
		{
			ChatUtils.error("仅限创造模式。");
			setEnabled(false);
			return;
		}
		
		// generate potion
		ItemStack stack = potionType.getSelected().createPotionStack();
		
		// give potion
		Inventory inventory = MC.player.getInventory();
		int slot = inventory.getFreeSlot();
		if(slot < 0)
			ChatUtils.error("无法给药水。背包已满。");
		else
		{
			InventoryUtils.setCreativeStack(slot, stack);
			ChatUtils.message("药水已创建。");
		}
		
		setEnabled(false);
	}
	
	private enum PotionType
	{
		NORMAL("Normal", "Potion", Items.POTION),
		
		SPLASH("Splash", "Splash Potion", Items.SPLASH_POTION),
		
		LINGERING("Lingering", "Lingering Potion", Items.LINGERING_POTION);
		
		// does not work
		// ARROW("Arrow", "Arrow", Items.TIPPED_ARROW);
		
		private final String name;
		private final String itemName;
		private final Item item;
		
		private PotionType(String name, String itemName, Item item)
		{
			this.name = name;
			this.itemName = itemName;
			this.item = item;
		}
		
		@Override
		public String toString()
		{
			return name;
		}
		
		public ItemStack createPotionStack()
		{
			ItemStack stack = new ItemStack(item);
			
			Holder<MobEffect> instantHealth =
				BuiltInRegistries.MOB_EFFECT.getHolder(6).orElseThrow();
			List<MobEffectInstance> effects = List.of(
				new MobEffectInstance(instantHealth, 2000, 125));
			stack.set(DataComponents.POTION_CONTENTS,
				new PotionContents(Optional.empty(), Optional.empty(), effects));
			
			String name =
				"\u00a7f" + itemName + " of \u00a74\u00a7lINSTANT DEATH";
			stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
			
			return stack;
		}
	}
}
