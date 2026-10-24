/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;

@SearchTags({"better tooltips", "shulker contents", "map scale", "food info",
	"tooltip"})
public final class BetterTooltipsHack extends Hack
{
	private final CheckboxSetting shulkerContents = new CheckboxSetting(
		"Shulker contents", "Lists the contents of shulker boxes.", true);

	private final CheckboxSetting mapScale = new CheckboxSetting("Map scale",
		"Shows the scale of filled maps.", true);

	private final CheckboxSetting foodValues = new CheckboxSetting(
		"Food values", "Shows hunger and saturation values of food.", true);

	public BetterTooltipsHack()
	{
		super("BetterTooltips");
		setCategory(Category.RENDER);
		addSetting(shulkerContents);
		addSetting(mapScale);
		addSetting(foodValues);
	}

	@Override
	protected void onEnable()
	{
		NeoForge.EVENT_BUS.register(this);
	}

	@Override
	protected void onDisable()
	{
		NeoForge.EVENT_BUS.unregister(this);
	}

	@SubscribeEvent
	public void onItemTooltip(ItemTooltipEvent event)
	{
		if(!isEnabled())
			return;

		ItemStack stack = event.getItemStack();
		List<Component> lines = event.getToolTip();

		if(foodValues.isChecked())
		{
			FoodProperties food = stack.get(DataComponents.FOOD);
			if(food != null)
				lines.add(Component
					.literal("Hunger: " + food.nutrition() + ", Saturation: "
						+ String.format("%.1f", food.saturation()))
					.withStyle(ChatFormatting.GRAY));
		}

		if(mapScale.isChecked() && stack.is(Items.FILLED_MAP)
			&& MC.level != null)
		{
			MapId id = stack.get(DataComponents.MAP_ID);
			MapItemSavedData data =
				id == null ? null : MC.level.getMapData(id);

			if(data != null)
				lines.add(Component
					.literal("Map scale: 1:" + (1 << data.scale))
					.withStyle(ChatFormatting.GRAY));
		}

		if(shulkerContents.isChecked())
			appendContainerContents(stack, lines);
	}

	private void appendContainerContents(ItemStack stack,
		List<Component> lines)
	{
		ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
		if(contents == null)
			return;

		int shown = 0;
		for(ItemStack content : contents.nonEmptyItems())
		{
			if(shown++ >= 5)
			{
				lines.add(Component.literal("... and more")
					.withStyle(ChatFormatting.DARK_GRAY));
				break;
			}

			MutableComponent line = Component.literal(" ")
				.append(Component.literal(content.getCount() + "x ")
					.withStyle(ChatFormatting.DARK_GRAY))
				.append(content.getHoverName().copy()
					.withStyle(ChatFormatting.GRAY));
			lines.add(line);
		}
	}
}
