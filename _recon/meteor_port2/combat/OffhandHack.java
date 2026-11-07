/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"offhand", "off-hand", "Offhand", "off hand item"})
public final class OffhandHack extends Hack implements UpdateListener
{
	private final EnumSetting<OffhandItem> item = new EnumSetting<>("Item",
		"Which item to keep in your offhand.", OffhandItem.values(),
		OffhandItem.TOTEM);

	private final SliderSetting totemHealth = new SliderSetting(
		"Totem health",
		"Always hold a totem when your health is at or below this value.\n"
			+ "0 = only hold the selected item",
		10, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" HP").withLabel(0, "off"));

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks to wait between inventory moves.", 0, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private int timer;

	public OffhandHack()
	{
		super("Offhand");
		setCategory(Category.COMBAT);
		addSetting(item);
		addSetting(totemHealth);
		addSetting(delay);
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
		WURST.getInventoryActionQueue().cancel(this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.player.isSpectator())
			return;

		// don't move items while a container is open
		if(MC.screen instanceof AbstractContainerScreen)
			return;
		if(MC.player.containerMenu.containerId != 0)
			return;

		Item wanted = item.getSelected().item;
		float health = MC.player.getHealth() + MC.player.getAbsorptionAmount();
		if(totemHealth.getValueI() > 0
			&& health <= totemHealth.getValueI())
			wanted = Items.TOTEM_OF_UNDYING;

		Item wantedItem = wanted;
		if(MC.player.getOffhandItem().is(wantedItem))
		{
			timer = 0;
			return;
		}

		if(timer > 0)
		{
			timer--;
			return;
		}

		int slot = InventoryUtils.indexOf(stack -> stack.is(wantedItem), 36);
		if(slot == -1)
			return;

		IMC.getInteractionManager().windowClick_SWAP(
			InventoryUtils.toNetworkSlot(slot), 40);
		timer = delay.getValueI();
	}

	public enum OffhandItem
	{
		TOTEM("Totem of Undying", Items.TOTEM_OF_UNDYING),
		SHIELD("Shield", Items.SHIELD),
		GAPPLE("Golden Apple", Items.GOLDEN_APPLE),
		EGAPPLE("Enchanted Golden Apple", Items.ENCHANTED_GOLDEN_APPLE),
		CRYSTAL("End Crystal", Items.END_CRYSTAL);

		private final String name;
		private final Item item;

		OffhandItem(String name, Item item)
		{
			this.name = name;
			this.item = item;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
