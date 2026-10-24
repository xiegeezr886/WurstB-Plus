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
 * Lowers the vanilla item-use cooldown while manually placing end crystals.
 *
 * <p>
 * The original module used a mixin to write the cooldown field. This port uses
 * Wurst's existing IMinecraftClient accessor instead, so no new mixin is
 * required.
 */
@SearchTags({"manual crystal", "fast place crystal", "crystal place",
	"ada manual crystal"})
public final class AdaManualCrystalHack extends Hack implements UpdateListener
{
	private final SliderSetting placeDelay = new SliderSetting("Place delay",
		"Target item-use cooldown in ticks. 1 or 2 is fast and relatively safe.",
		1, 0, 4, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting jitter = new CheckboxSetting("Jitter",
		"Randomly adds one tick of cooldown to look more human.", true);

	public AdaManualCrystalHack()
	{
		super("AdaManualCrystal");
		setCategory(Category.COMBAT);
		addSetting(placeDelay);
		addSetting(jitter);
	}

	@Override
	protected void onEnable()
	{
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
		if(MC.player == null || MC.gameMode == null)
			return;

		boolean holdingCrystal =
			MC.player.getMainHandItem().is(Items.END_CRYSTAL)
				|| MC.player.getOffhandItem().is(Items.END_CRYSTAL);

		if(!holdingCrystal || !MC.options.keyUse.isDown())
			return;

		int target = placeDelay.getValueI();
		if(jitter.isChecked() && target < 3
			&& ThreadLocalRandom.current().nextBoolean())
			target++;

		if(MC.rightClickDelay > target)
			MC.rightClickDelay = target;
	}
}
