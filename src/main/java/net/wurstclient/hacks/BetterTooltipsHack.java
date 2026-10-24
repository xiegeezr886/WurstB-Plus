/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"better tooltips", "tooltip", "item info", "durability"})
public final class BetterTooltipsHack extends Hack
{
	private final CheckboxSetting showDurability = new CheckboxSetting(
		"Show durability", "Show numeric durability on damaged items.", true);
	private final CheckboxSetting showItemId =
		new CheckboxSetting("Show item ID", "Show the item registry name.", false);
	private final CheckboxSetting showFood = new CheckboxSetting("Show food",
		"Show food nutrition and saturation.", true);

	public BetterTooltipsHack()
	{
		super("BetterTooltips");
		setCategory(Category.OTHER);
		addSetting(showDurability);
		addSetting(showItemId);
		addSetting(showFood);
	}

	@Override
	protected void onEnable()
	{
		MinecraftForge.EVENT_BUS.register(this);
	}

	@Override
	protected void onDisable()
	{
		MinecraftForge.EVENT_BUS.unregister(this);
	}

	@SubscribeEvent
	public void onItemTooltip(ItemTooltipEvent event)
	{
		ItemStack stack = event.getItemStack();
		if(stack.isEmpty())
			return;
		List<Component> lines = event.getToolTip();

		if(showDurability.isChecked() && stack.isDamageableItem())
		{
			int left = stack.getMaxDamage() - stack.getDamageValue();
			lines.add(Component
				.literal("Durability: " + left + " / " + stack.getMaxDamage())
				.withStyle(ChatFormatting.GRAY));
		}
		if(showItemId.isChecked())
			lines.add(Component
				.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
				.withStyle(ChatFormatting.DARK_GRAY));
		if(showFood.isChecked() && stack.getItem().isEdible())
		{
			var food = stack.getItem().getFoodProperties();
			if(food != null)
				lines.add(Component
					.literal("Food: " + food.getNutrition() + " / "
						+ food.getSaturationModifier())
					.withStyle(ChatFormatting.GOLD));
		}
	}
}
