/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
/*
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"elytra boost", "rocket", "firework"})
public final class ElytraBoostHack extends Hack implements UpdateListener
{
	private final SliderSetting interval = new SliderSetting("Interval",
		"Ticks to wait between firework boosts.", 20, 1, 100, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting onlyWhenLookingUp = new CheckboxSetting(
		"Only when looking up",
		"Only boost while the player is looking upwards.", false);

	private int cooldown;

	public ElytraBoostHack()
	{
		super("ElytraBoost");
		setCategory(Category.MOVEMENT);
		addSetting(interval);
		addSetting(onlyWhenLookingUp);
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
		cooldown = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(!MC.player.isFallFlying())
		{
			cooldown = 0;
			return;
		}

		if(MC.screen != null)
			return;

		if(onlyWhenLookingUp.isChecked() && MC.player.getXRot() >= 0)
			return;

		if(cooldown > 0)
		{
			cooldown--;
			return;
		}

		int slot = InventoryUtils.indexOf(Items.FIREWORK_ROCKET, 9);
		if(slot == -1)
			return;

		InventoryUtils.selectItem(slot);
		if(!MC.player.getMainHandItem().is(Items.FIREWORK_ROCKET))
			return;

		MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
		cooldown = interval.getValueI();
	}
}
