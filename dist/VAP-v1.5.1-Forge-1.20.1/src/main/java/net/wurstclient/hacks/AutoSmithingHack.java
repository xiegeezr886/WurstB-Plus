/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import java.util.function.Predicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.ItemListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Automates the smithing table while its screen is open.
 *
 * <p>
 * Items are moved with the local window-click helpers (pickup sequences)
 * rather than by opening the player inventory, so the smithing screen stays
 * focused.
 */
@SearchTags({"auto smithing", "smithing table", "netherite upgrade",
	"armor trim", "auto upgrade", "auto trim"})
public final class AutoSmithingHack extends Hack implements UpdateListener
{
	public enum Mode
	{
		UPGRADE("Upgrade"),
		TRIM("Trim");

		private final String name;

		private Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Upgrade: netherite upgrades.\nTrim: armor trims.", Mode.values(),
		Mode.UPGRADE);

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks to wait between inventory actions.", 3, 1, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting autoDrop = new CheckboxSetting("Auto drop",
		"Throws the finished result on the ground instead of moving it to the"
			+ " inventory.",
		false);

	private final ItemListSetting upgradeTargets = new ItemListSetting(
		"Upgrade targets", "Diamond gear that should be upgraded.",
		"minecraft:diamond_sword", "minecraft:diamond_axe",
		"minecraft:diamond_pickaxe", "minecraft:diamond_shovel",
		"minecraft:diamond_hoe", "minecraft:diamond_helmet",
		"minecraft:diamond_chestplate", "minecraft:diamond_leggings",
		"minecraft:diamond_boots");

	private final ItemListSetting trimTargets = new ItemListSetting(
		"Trim targets", "Armor pieces that should receive a trim.",
		"minecraft:netherite_chestplate", "minecraft:diamond_chestplate");

	private final ItemListSetting trimTemplates = new ItemListSetting(
		"Trim templates", "Armor trim smithing templates to use.",
		"minecraft:sentry_armor_trim_smithing_template",
		"minecraft:vex_armor_trim_smithing_template",
		"minecraft:wild_armor_trim_smithing_template",
		"minecraft:coast_armor_trim_smithing_template",
		"minecraft:dune_armor_trim_smithing_template",
		"minecraft:wayfinder_armor_trim_smithing_template",
		"minecraft:raiser_armor_trim_smithing_template",
		"minecraft:shaper_armor_trim_smithing_template",
		"minecraft:host_armor_trim_smithing_template",
		"minecraft:ward_armor_trim_smithing_template",
		"minecraft:silence_armor_trim_smithing_template",
		"minecraft:tide_armor_trim_smithing_template",
		"minecraft:snout_armor_trim_smithing_template",
		"minecraft:rib_armor_trim_smithing_template",
		"minecraft:eye_armor_trim_smithing_template",
		"minecraft:spire_armor_trim_smithing_template");

	private final ItemListSetting trimMaterials = new ItemListSetting(
		"Trim materials", "Materials used for the trim color.",
		"minecraft:iron_ingot", "minecraft:copper_ingot",
		"minecraft:gold_ingot", "minecraft:lapis_lazuli", "minecraft:emerald",
		"minecraft:diamond", "minecraft:netherite_ingot", "minecraft:redstone",
		"minecraft:amethyst_shard", "minecraft:quartz");

	private int timer;

	public AutoSmithingHack()
	{
		super("AutoSmithing");
		setCategory(Category.ITEMS);
		addSetting(mode);
		addSetting(delay);
		addSetting(autoDrop);
		addSetting(upgradeTargets);
		addSetting(trimTargets);
		addSetting(trimTemplates);
		addSetting(trimMaterials);

		upgradeTargets.visibleWhen(() -> mode.getSelected() == Mode.UPGRADE);
		trimTargets.visibleWhen(() -> mode.getSelected() == Mode.TRIM);
		trimTemplates.visibleWhen(() -> mode.getSelected() == Mode.TRIM);
		trimMaterials.visibleWhen(() -> mode.getSelected() == Mode.TRIM);
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
		if(MC.player == null || MC.level == null || MC.player.isSpectator())
			return;

		if(!(MC.player.containerMenu instanceof SmithingMenu menu))
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		if(!menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty())
		{
			if(autoDrop.isChecked())
				IMC.getInteractionManager()
					.windowClick_THROW(SmithingMenu.RESULT_SLOT);
			else
				IMC.getInteractionManager()
					.windowClick_QUICK_MOVE(SmithingMenu.RESULT_SLOT);

			timer = delay.getValueI();
			return;
		}

		if(mode.getSelected() == Mode.UPGRADE)
			handleUpgrade(menu);
		else
			handleTrim(menu);
	}

	private void handleUpgrade(SmithingMenu menu)
	{
		if(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()
			&& tryMove(menu, stack -> stack
				.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
				SmithingMenu.TEMPLATE_SLOT))
			return;

		if(menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty()
			&& tryMove(menu, stack -> stack.is(Items.NETHERITE_INGOT),
				SmithingMenu.ADDITIONAL_SLOT))
			return;

		if(menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty())
			tryMove(menu, stack -> matches(stack, upgradeTargets),
				SmithingMenu.BASE_SLOT);
	}

	private void handleTrim(SmithingMenu menu)
	{
		if(menu.getSlot(SmithingMenu.TEMPLATE_SLOT).getItem().isEmpty()
			&& tryMove(menu, stack -> matches(stack, trimTemplates),
				SmithingMenu.TEMPLATE_SLOT))
			return;

		if(menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty()
			&& tryMove(menu, stack -> matches(stack, trimMaterials),
				SmithingMenu.ADDITIONAL_SLOT))
			return;

		if(menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty())
			tryMove(menu, stack -> matches(stack, trimTargets),
				SmithingMenu.BASE_SLOT);
	}

	private boolean tryMove(SmithingMenu menu, Predicate<ItemStack> predicate,
		int targetSlot)
	{
		int sourceSlot = findSlot(menu, predicate);
		if(sourceSlot == -1)
			return false;

		moveToSlot(sourceSlot, targetSlot);
		timer = delay.getValueI();
		return true;
	}

	private int findSlot(SmithingMenu menu, Predicate<ItemStack> predicate)
	{
		for(int i = SmithingMenu.RESULT_SLOT + 1; i < menu.slots.size(); i++)
		{
			ItemStack stack = menu.getSlot(i).getItem();
			if(!stack.isEmpty() && predicate.test(stack))
				return i;
		}

		return -1;
	}

	private void moveToSlot(int sourceSlot, int targetSlot)
	{
		IMC.getInteractionManager().windowClick_PICKUP(sourceSlot);
		IMC.getInteractionManager().windowClick_PICKUP(targetSlot);

		if(!MC.player.containerMenu.getCarried().isEmpty())
			IMC.getInteractionManager().windowClick_PICKUP(sourceSlot);
	}

	private boolean matches(ItemStack stack, ItemListSetting list)
	{
		String name = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		return list.getItemNames().contains(name);
	}
}
