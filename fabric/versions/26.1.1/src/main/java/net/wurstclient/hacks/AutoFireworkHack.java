/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from InvincibleMachineGun (GPL-3.0).
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IClientPlayerInteractionManager;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

/**
 * Uses firework rockets while elytra flying.
 *
 * <p>
 * The original module had its own key binding. Wurst already reserves module
 * key binds for toggling the module, so this port triggers while the vanilla
 * use key is held (configurable).
 */
@SearchTags({"auto firework", "firework", "elytra boost", "rocket"})
public final class AutoFireworkHack extends Hack implements UpdateListener
{
	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks to wait between firework uses.", 10, 1, 40, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting requireUseKey = new CheckboxSetting(
		"Require use key", "Only uses fireworks while the use key is held.",
		true);

	private final CheckboxSetting silentSwitch = new CheckboxSetting(
		"Silent switch",
		"Switches to a hotbar firework with packets and switches back"
			+ " immediately.",
		true);

	private int timer;

	public AutoFireworkHack()
	{
		super("AutoFirework");
		setCategory(Category.MOVEMENT);
		addSetting(delay);
		addSetting(requireUseKey);
		addSetting(silentSwitch);
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
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		if(timer > 0)
			timer--;

		if(!MC.player.isFallFlying())
			return;

		if(requireUseKey.isChecked() && !MC.options.keyUse.isDown())
			return;

		if(timer > 0)
			return;

		ItemStack mainHand = MC.player.getMainHandItem();
		if(isFirework(mainHand))
		{
			useFirework(InteractionHand.MAIN_HAND);
			return;
		}

		if(isFirework(MC.player.getOffhandItem()))
		{
			useFirework(InteractionHand.OFF_HAND);
			return;
		}

		int slot = findFireworkSlot();
		if(slot == -1)
			return;

		if(silentSwitch.isChecked())
		{
			int oldSlot = MC.player.getInventory().getSelectedSlot();
			MC.getConnection()
				.send(new ServerboundSetCarriedItemPacket(slot));
			((IClientPlayerInteractionManager)MC.gameMode).sendPlayerUseItemPacket(InteractionHand.MAIN_HAND);
			MC.getConnection()
				.send(new ServerboundSetCarriedItemPacket(oldSlot));
		}else
		{
			InventoryUtils.selectItem(slot);
			useFirework(InteractionHand.MAIN_HAND);
		}

		timer = delay.getValueI();
	}

	private void useFirework(InteractionHand hand)
	{
		MC.gameMode.useItem(MC.player, hand);
		MC.player.swing(hand);
		timer = delay.getValueI();
	}

	private int findFireworkSlot()
	{
		for(int i = 0; i < 9; i++)
			if(isFirework(MC.player.getInventory().getItem(i)))
				return i;

		return -1;
	}

	private boolean isFirework(ItemStack stack)
	{
		return stack.is(Items.FIREWORK_ROCKET);
	}
}
