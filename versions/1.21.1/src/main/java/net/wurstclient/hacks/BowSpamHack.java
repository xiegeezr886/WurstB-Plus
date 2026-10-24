/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"auto bow", "crossbow spam", "auto crossbow", "bow spam"})
public final class BowSpamHack extends Hack implements UpdateListener
{
	private final SliderSetting charge = new SliderSetting("Charge",
		"How long to charge the bow before releasing in ticks.", 5, 4, 20, 1,
		ValueDisplay.INTEGER);

	private final CheckboxSetting onlyWhenHoldingRightClick =
		new CheckboxSetting("When holding right click",
			"Works only when holding right click.", false);

	private final CheckboxSetting spamCrossbows = new CheckboxSetting(
		"Spam crossbows",
		"Whether to spam loaded crossbows; takes priority over charging bows.",
		true);

	private final SliderSetting crossbowDelay = new SliderSetting(
		"Crossbow delay", "Delay between shooting crossbows in ticks.", 10, 0,
		20, 1, ValueDisplay.INTEGER);

	private boolean charging;
	private int chargeTicks;
	private int crossbowTicks;

	public BowSpamHack()
	{
		super("BowSpam");
		setCategory(Category.COMBAT);
		addSetting(charge);
		addSetting(onlyWhenHoldingRightClick);
		addSetting(spamCrossbows);
		addSetting(crossbowDelay);
	}

	@Override
	protected void onEnable()
	{
		charging = false;
		chargeTicks = 0;
		crossbowTicks = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		stopCharging();
	}

	@Override
	public void onUpdate()
	{
		if(spamCrossbows.isChecked() && shootCrossbow())
			return;

		if(!MC.player.getAbilities().instabuild && InventoryUtils
			.indexOf(stack -> stack.getItem() instanceof ArrowItem) == -1)
		{
			stopCharging();
			return;
		}

		boolean isBow = MC.player.getMainHandItem().is(Items.BOW)
			|| MC.player.getOffhandItem().is(Items.BOW);
		if(!isBow)
		{
			stopCharging();
			return;
		}

		if(onlyWhenHoldingRightClick.isChecked() && !MC.options.keyUse.isDown()
			&& !charging)
			return;

		if(!charging)
		{
			charging = true;
			chargeTicks = 0;
		}
		MC.options.keyUse.setDown(true);
		chargeTicks++;

		if(chargeTicks >= charge.getValueI())
		{
			MC.gameMode.releaseUsingItem(MC.player);
			stopCharging();
		}
	}

	private boolean shootCrossbow()
	{
		if(crossbowTicks++ < crossbowDelay.getValueI())
			return hasChargedCrossbow();
		crossbowTicks = 0;

		int slot = InventoryUtils.indexOf(
			stack -> stack.getItem() instanceof CrossbowItem
				&& CrossbowItem.isCharged(stack), 9);
		if(slot == -1)
			return false;

		InventoryUtils.selectItem(slot);
		if(MC.player.getMainHandItem().getItem() instanceof CrossbowItem)
			MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
		return true;
	}

	private boolean hasChargedCrossbow()
	{
		return InventoryUtils.indexOf(
			stack -> stack.getItem() instanceof CrossbowItem
				&& CrossbowItem.isCharged(stack), 9) != -1;
	}

	private void stopCharging()
	{
		MC.options.keyUse.setDown(false);
		charging = false;
		chargeTicks = 0;
	}
}
