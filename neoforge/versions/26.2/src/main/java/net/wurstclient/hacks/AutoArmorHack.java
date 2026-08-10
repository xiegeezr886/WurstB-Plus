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
		
		// TODO: 26.1.2 - getArmor() removed, need to refactor to use EquipmentSlot
		// For now, skip armor swapping
		return;
	}
	
	@Override
	public void onSentPacket(PacketOutputEvent event)
	{
		if(event.getPacket() instanceof ServerboundContainerClickPacket)
			timer = delay.getValueI();
	}
	
	private int getArmorValue(ItemStack stack)
	{
		// ArmorItem removed in MC 26.1.2
		return 0;
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
