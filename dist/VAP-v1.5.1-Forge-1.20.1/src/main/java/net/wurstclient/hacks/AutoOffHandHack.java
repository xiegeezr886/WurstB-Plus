/*
 * Copyright (c) 2025 LeavesHack contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from LeavesHack (AGPL-3.0).
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
import net.wurstclient.util.text.WText;

@SearchTags({"auto offhand", "offhand", "off-hand", "totem", "shield",
	"AutoOffHand"})
public final class AutoOffHandHack extends Hack implements UpdateListener
{
	private static final int OFFHAND_SLOT = 40;

	private final EnumSetting<OffhandItem> item = new EnumSetting<>("Item",
		WText.literal("Which item to keep in the offhand."),
		OffhandItem.values(), OffhandItem.TOTEM);

	private final SliderSetting totemHealth = new SliderSetting(
		"Totem health",
		WText.literal("Holds a totem instead when your health plus absorption"
			+ " reaches this value or falls below it.\n0 = disabled"),
		10, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" HP").withLabel(0, "off"));

	private final SliderSetting delay = new SliderSetting("Delay",
		WText.literal("Ticks to wait between inventory moves."), 0, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private int timer;

	public AutoOffHandHack()
	{
		super("AutoOffHand");
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
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.player.isSpectator())
			return;

		if(MC.screen instanceof AbstractContainerScreen)
			return;
		if(MC.player.containerMenu.containerId != 0)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		Item wanted = item.getSelected().item;
		float health = MC.player.getHealth() + MC.player.getAbsorptionAmount();
		if(totemHealth.getValueI() > 0
			&& health <= totemHealth.getValueI()
			&& InventoryUtils.indexOf(Items.TOTEM_OF_UNDYING, 36) != -1)
			wanted = Items.TOTEM_OF_UNDYING;

		if(MC.player.getOffhandItem().is(wanted))
			return;

		int slot = InventoryUtils.indexOf(wanted, 36);
		if(slot == -1)
			return;

		IMC.getInteractionManager().windowClick_SWAP(
			InventoryUtils.toNetworkSlot(slot), OFFHAND_SLOT);
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
