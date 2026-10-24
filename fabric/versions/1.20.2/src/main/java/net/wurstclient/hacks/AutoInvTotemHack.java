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

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;

/**
 * Legit-style offhand totem refill. Waits for a totem pop (or a locally
 * observed offhand change), then swaps a totem from the player inventory with
 * a human-like delay while the inventory screen is open.
 */
@SearchTags({"auto inventory totem", "legit totem", "offhand totem",
	"totem pop"})
public final class AutoInvTotemHack extends Hack
	implements UpdateListener, PacketInputListener
{
	private final SliderSetting minDelay = new SliderSetting("Minimum delay",
		"Minimum delay before the swap, in milliseconds.", 200, 50, 1000, 10,
		ValueDisplay.INTEGER.withSuffix(" ms"));

	private final SliderSetting maxDelay = new SliderSetting("Maximum delay",
		"Maximum delay before the swap, in milliseconds.", 350, 50, 1000, 10,
		ValueDisplay.INTEGER.withSuffix(" ms"));

	private final CheckboxSetting moveFromHotbar = new CheckboxSetting(
		"Move from hotbar", "Also takes totems out of the hotbar.", true);

	private final CheckboxSetting notifications = new CheckboxSetting(
		"Notifications", "Shows a chat message after swapping a totem.", false);

	private boolean needsTotem;
	private boolean hadOffhandTotem;
	private long executeTime;

	public AutoInvTotemHack()
	{
		super("AutoInvTotem");
		setCategory(Category.COMBAT);
		addSetting(minDelay);
		addSetting(maxDelay);
		addSetting(moveFromHotbar);
		addSetting(notifications);
	}

	@Override
	protected void onEnable()
	{
		resetState();
		EVENTS.add(UpdateListener.class, this);
		EVENTS.add(PacketInputListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		EVENTS.remove(PacketInputListener.class, this);
		resetState();
	}

	private void resetState()
	{
		needsTotem = false;
		executeTime = 0;
		hadOffhandTotem = hasOffhandTotem();
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(MC.player == null || MC.level == null)
			return;

		if(!(event.getPacket() instanceof ClientboundEntityEventPacket packet)
			|| packet.getEventId() != EntityEvent.TALISMAN_ACTIVATE)
			return;

		Entity entity = packet.getEntity(MC.level);
		if(entity != MC.player)
			return;

		scheduleSwap();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.player.isSpectator())
			return;

		boolean hasTotem = hasOffhandTotem();

		if(hadOffhandTotem && !hasTotem && !needsTotem)
			scheduleSwap();

		hadOffhandTotem = hasTotem;

		if(hasTotem && needsTotem)
		{
			needsTotem = false;
			return;
		}

		if(!needsTotem || !(MC.screen instanceof InventoryScreen))
			return;

		if(System.currentTimeMillis() < executeTime)
			return;

		int totemSlot = findTotemSlot();
		if(totemSlot == -1)
			return;

		IMC.getInteractionManager()
			.windowClick_SWAP(InventoryUtils.toNetworkSlot(totemSlot), 40);
		needsTotem = false;
		executeTime = System.currentTimeMillis() + 500;

		if(notifications.isChecked())
			ChatUtils.message("AutoInvTotem: swapped a totem to the offhand.");
	}

	private void scheduleSwap()
	{
		needsTotem = true;
		int min = minDelay.getValueI();
		int max = Math.max(min, maxDelay.getValueI());
		executeTime = System.currentTimeMillis()
			+ ThreadLocalRandom.current().nextInt(min, max + 1);
	}

	private int findTotemSlot()
	{
		for(int i = 9; i < 36; i++)
			if(isTotem(MC.player.getInventory().getItem(i)))
				return i;

		if(!moveFromHotbar.isChecked())
			return -1;

		for(int i = 0; i < 9; i++)
			if(isTotem(MC.player.getInventory().getItem(i)))
				return i;

		return -1;
	}

	private boolean hasOffhandTotem()
	{
		return MC.player != null && isTotem(MC.player.getOffhandItem());
	}

	private boolean isTotem(ItemStack stack)
	{
		return !stack.isEmpty() && stack.is(Items.TOTEM_OF_UNDYING);
	}
}
