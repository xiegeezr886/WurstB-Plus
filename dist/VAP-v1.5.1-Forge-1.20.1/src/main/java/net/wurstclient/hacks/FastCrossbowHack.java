/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from InvincibleMachineGun (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"fast crossbow", "crossbow", "auto crossbow", "rocket",
	"firework", "FastCrossbow"})
public final class FastCrossbowHack extends Hack implements UpdateListener
{
	private final SliderSetting delay =
		new SliderSetting("Delay", "Delay between shots in ticks.", 3, 0, 20, 1,
			ValueDisplay.INTEGER);

	private final CheckboxSetting onlyWhenHolding = new CheckboxSetting(
		"Only when holding right click",
		"Only charges and shoots while you hold right click.", true);

	private final CheckboxSetting multiShot = new CheckboxSetting("Multi shot",
		"Switches to other charged crossbows in the hotbar after each shot.",
		false);

	private int cooldown;

	public FastCrossbowHack()
	{
		super("FastCrossbow");
		setCategory(Category.COMBAT);
		addSetting(delay);
		addSetting(onlyWhenHolding);
		addSetting(multiShot);
	}

	@Override
	protected void onEnable()
	{
		cooldown = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		stopCharging();
		cooldown = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(onlyWhenHolding.isChecked() && !MC.options.keyUse.isDown())
		{
			stopCharging();
			cooldown = 0;
			return;
		}

		if(cooldown > 0)
		{
			cooldown--;
			return;
		}

		InteractionHand hand = selectCrossbow();
		if(hand == null)
		{
			stopCharging();
			return;
		}

		ItemStack stack = MC.player.getItemInHand(hand);
		if(!(stack.getItem() instanceof CrossbowItem))
		{
			stopCharging();
			return;
		}

		if(CrossbowItem.isCharged(stack))
		{
			// let go of the charge before firing
			if(isUsingCrossbow())
			{
				MC.gameMode.releaseUsingItem(MC.player);
				return;
			}

			MC.gameMode.useItem(MC.player, hand);
			MC.player.swing(hand);
			cooldown = delay.getValueI();
			return;
		}

		// start charging, but don't interrupt other items in use
		if(!MC.player.isUsingItem())
			MC.gameMode.useItem(MC.player, hand);
	}

	private InteractionHand selectCrossbow()
	{
		if(multiShot.isChecked())
		{
			int chargedSlot = findChargedCrossbowSlot();
			if(chargedSlot != -1)
			{
				MC.player.getInventory().selected = chargedSlot;
				return InteractionHand.MAIN_HAND;
			}
		}

		if(MC.player.getMainHandItem().getItem() instanceof CrossbowItem)
			return InteractionHand.MAIN_HAND;

		if(MC.player.getOffhandItem().getItem() instanceof CrossbowItem)
			return InteractionHand.OFF_HAND;

		int slot = findCrossbowSlot();
		if(slot == -1)
			return null;

		MC.player.getInventory().selected = slot;
		return InteractionHand.MAIN_HAND;
	}

	private int findChargedCrossbowSlot()
	{
		// charged crossbows may hold arrows or firework rockets
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.getItem() instanceof CrossbowItem
				&& CrossbowItem.isCharged(stack))
				return i;
		}

		return -1;
	}

	private int findCrossbowSlot()
	{
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.getItem() instanceof CrossbowItem)
				return i;
		}

		return -1;
	}

	private boolean isUsingCrossbow()
	{
		return MC.player.isUsingItem()
			&& MC.player.getUseItem().getItem() instanceof CrossbowItem;
	}

	private void stopCharging()
	{
		if(MC.player != null && isUsingCrossbow())
			MC.gameMode.releaseUsingItem(MC.player);
	}
}
