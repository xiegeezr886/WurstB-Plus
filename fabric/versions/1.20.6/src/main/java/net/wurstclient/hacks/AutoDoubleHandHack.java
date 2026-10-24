/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/**
 * Switches to a hotbar totem after the offhand totem pops.
 *
 * <p>
 * The original module used a configurable switch delay plus a small random
 * jitter to look less mechanical. Both behaviours are preserved here.
 */
@SearchTags({"auto double hand", "double hand", "offhand totem", "totem swap"})
public final class AutoDoubleHandHack extends Hack implements UpdateListener
{
	private final SliderSetting delay = new SliderSetting("Switch delay",
		"Ticks to wait before switching to the hotbar totem.", 2, 1, 10, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting jitter = new CheckboxSetting("Jitter",
		"Randomly adds one extra tick of delay.", true);

	private boolean wasHoldingTotem;
	private boolean needsSwitch;
	private int delayTimer;

	public AutoDoubleHandHack()
	{
		super("AutoDoubleHand");
		setCategory(Category.COMBAT);
		addSetting(delay);
		addSetting(jitter);
	}

	@Override
	protected void onEnable()
	{
		wasHoldingTotem = hasOffhandTotem();
		needsSwitch = false;
		delayTimer = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		needsSwitch = false;
		delayTimer = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.player.isSpectator())
			return;

		boolean holdingNow = hasOffhandTotem();

		if(wasHoldingTotem && !holdingNow)
		{
			needsSwitch = true;
			delayTimer = delay.getValueI()
				+ (jitter.isChecked() && ThreadLocalRandom.current().nextBoolean()
					? 1 : 0);
		}

		wasHoldingTotem = holdingNow;

		if(!needsSwitch)
			return;

		if(delayTimer > 0)
		{
			delayTimer--;
			return;
		}

		int slot = findHotbarTotem();
		needsSwitch = false;

		if(slot == -1 || MC.player.getMainHandItem().is(Items.TOTEM_OF_UNDYING))
			return;

		MC.player.getInventory().selected = slot;
	}

	private int findHotbarTotem()
	{
		for(int i = 0; i < 9; i++)
			if(MC.player.getInventory().getItem(i)
				.is(Items.TOTEM_OF_UNDYING))
				return i;

		return -1;
	}

	private boolean hasOffhandTotem()
	{
		return MC.player != null
			&& MC.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
	}
}
