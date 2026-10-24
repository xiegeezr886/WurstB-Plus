/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

@SearchTags({"auto gap", "autogap", "golden apple", "AutoGapple"})
public final class AutoGapHack extends Hack implements UpdateListener
{
	private final SliderSetting health = new SliderSetting("Health",
		"Eats a golden apple when your health plus absorption reaches this"
			+ " value or falls below it.",
		10, 0.5, 20, 0.5, ValueDisplay.DECIMAL.withSuffix(" hearts"));

	private final CheckboxSetting allowEgap = new CheckboxSetting(
		"Allow enchanted golden apple",
		"Uses enchanted golden apples if they are available.", true);

	private int oldSlot = -1;

	public AutoGapHack()
	{
		super("AutoGap");
		setCategory(Category.COMBAT);
		addSetting(health);
		addSetting(allowEgap);
	}

	@Override
	protected void onEnable()
	{
		WURST.getHax().autoEatHack.setEnabled(false);
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		stopEating();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.player.isSpectator())
		{
			stopEating();
			return;
		}

		if(MC.screen != null)
		{
			stopEating();
			return;
		}

		float effectiveHealth =
			MC.player.getHealth() + MC.player.getAbsorptionAmount();
		int gapSlot = effectiveHealth < health.getValueF() * 2F
			? findGapInHotbar()
			: -1;

		if(gapSlot == -1)
		{
			stopEating();
			return;
		}

		if(oldSlot == -1)
			oldSlot = MC.player.getInventory().selected;

		MC.player.getInventory().selected = gapSlot;
		MC.options.keyUse.setDown(true);

		if(!MC.player.isUsingItem())
			IMC.getInteractionManager().rightClickItem();
	}

	private int findGapInHotbar()
	{
		int normalGap = -1;

		for(int slot = 0; slot < 9; slot++)
		{
			ItemStack stack = MC.player.getInventory().getItem(slot);

			if(allowEgap.isChecked()
				&& stack.is(Items.ENCHANTED_GOLDEN_APPLE))
				return slot;

			if(normalGap == -1 && stack.is(Items.GOLDEN_APPLE))
				normalGap = slot;
		}

		return normalGap;
	}

	private void stopEating()
	{
		if(oldSlot == -1)
			return;

		MC.options.keyUse.setDown(false);

		if(MC.player != null)
			MC.player.getInventory().selected = oldSlot;

		oldSlot = -1;
	}
}
