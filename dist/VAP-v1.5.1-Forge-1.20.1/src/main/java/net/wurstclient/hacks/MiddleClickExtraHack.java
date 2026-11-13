/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
// Ported from Meteor Client (GPL-3.0).
package net.wurstclient.hacks;

import org.lwjgl.glfw.GLFW;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;

@SearchTags({"middle click", "middleclick", "MiddleClickExtra"})
public final class MiddleClickExtraHack extends Hack implements UpdateListener
{
	private final EnumSetting<Mode> mode =
		new EnumSetting<>("Mode", "Which item to use when you middle click.",
			Mode.values(), Mode.PEARL);

	private final CheckboxSetting quickSwap = new CheckboxSetting("Quick swap",
		"Allows you to use items in your inventory by simulating hotbar key"
			+ " presses. May get flagged by anticheats.",
		false);

	private final CheckboxSetting swapBack = new CheckboxSetting("Swap back",
		"Swap back to your original slot when you finish using an item.",
		false);

	private final CheckboxSetting disableInCreative =
		new CheckboxSetting("Disable in creative",
			"Middle click action is disabled in Creative mode.", true);

	private final CheckboxSetting notify = new CheckboxSetting("Notify",
		"Notifies you when you do not have the specified item in your"
			+ " hotbar.",
		true);

	private boolean wasMiddleHeld;
	private boolean isUsing;
	private boolean sawUsing;
	private int useTicks;
	private boolean wasHeld;
	private int itemSlot = -1;
	private int selectedSlot = -1;
	private int usingSlot = -1;

	public MiddleClickExtraHack()
	{
		super("MiddleClickExtra");
		setCategory(Category.ITEMS);
		addSetting(mode);
		addSetting(quickSwap);
		addSetting(swapBack);
		addSetting(disableInCreative);
		addSetting(notify);
	}

	@Override
	protected void onEnable()
	{
		wasMiddleHeld = false;
		resetState();
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);

		if(isUsing)
			stopUsing(true);

		wasMiddleHeld = false;
		resetState();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null)
		{
			wasMiddleHeld = false;
			resetState();
			return;
		}

		if(isUsing)
		{
			updateUsing();
			return;
		}

		if(MC.screen != null)
		{
			wasMiddleHeld = false;
			return;
		}

		long handle = MC.getWindow().getWindow();
		boolean held = GLFW.glfwGetMouseButton(handle,
			GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
		boolean pressed = held && !wasMiddleHeld;
		wasMiddleHeld = held;

		if(pressed)
			onMiddleClick();
	}

	private void onMiddleClick()
	{
		if(disableInCreative.isChecked()
			&& MC.player.getAbilities().instabuild)
			return;

		Mode selectedMode = mode.getSelected();
		int slot = InventoryUtils.indexOf(selectedMode.item, 36, false);

		if(slot == -1 || slot >= 9 && !quickSwap.isChecked())
		{
			if(notify.isChecked())
				ChatUtils.warning("Unable to find " + selectedMode
					+ " in your hotbar.");
			return;
		}

		selectedSlot = MC.player.getInventory().selected;
		itemSlot = slot;
		wasHeld = slot == selectedSlot;

		if(!wasHeld)
		{
			if(quickSwap.isChecked())
				IMC.getInteractionManager().windowClick_SWAP(
					InventoryUtils.toNetworkSlot(slot), selectedSlot);
			else
				MC.player.getInventory().selected = slot;
		}

		usingSlot = wasHeld || quickSwap.isChecked() ? selectedSlot
			: itemSlot;

		if(selectedMode.immediate)
		{
			MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
			swapBack(false);
			resetState();
			return;
		}

		MC.options.keyUse.setDown(true);
		IMC.getInteractionManager().rightClickItem();
		isUsing = true;
		sawUsing = false;
		useTicks = 0;
	}

	private void updateUsing()
	{
		useTicks++;

		// stop if the player switched to another slot or left the world
		if(MC.player == null || MC.player.isSpectator()
			|| MC.player.getInventory().selected != usingSlot)
		{
			stopUsing(true);
			return;
		}

		if(MC.player.isUsingItem())
			sawUsing = true;
		else if(sawUsing)
		{
			// the item was fully consumed or released
			stopUsing(false);
			return;

		}else if(useTicks > 5)
		{
			// the item never started being used
			stopUsing(true);
			return;
		}

		// release a bow once it is fully charged
		ItemStack stack = MC.player.getMainHandItem();
		if(stack.getItem() instanceof BowItem
			&& BowItem.getPowerForTime(
				MC.player.getTicksUsingItem()) >= 1F)
			stopUsing(false);
	}

	private void stopUsing(boolean cancelled)
	{
		if(!isUsing)
			return;

		isUsing = false;
		sawUsing = false;
		MC.options.keyUse.setDown(false);
		swapBack(cancelled);
		resetState();
	}

	private void swapBack(boolean cancelled)
	{
		if(wasHeld || MC.player == null)
			return;

		if(quickSwap.isChecked())
			IMC.getInteractionManager().windowClick_SWAP(
				InventoryUtils.toNetworkSlot(itemSlot), selectedSlot);
		else if(swapBack.isChecked() && !cancelled)
			MC.player.getInventory().selected = selectedSlot;
	}

	private void resetState()
	{
		isUsing = false;
		sawUsing = false;
		useTicks = 0;
		wasHeld = false;
		itemSlot = -1;
		selectedSlot = -1;
		usingSlot = -1;
	}

	private enum Mode
	{
		PEARL("Pearl", Items.ENDER_PEARL, true),
		XP("XP", Items.EXPERIENCE_BOTTLE, true),
		ROCKET("Rocket", Items.FIREWORK_ROCKET, true),

		BOW("Bow", Items.BOW, false),
		GAP("Gap", Items.GOLDEN_APPLE, false),
		EGAP("EGap", Items.ENCHANTED_GOLDEN_APPLE, false),
		CHORUS("Chorus", Items.CHORUS_FRUIT, false);

		private final String name;
		private final Item item;
		private final boolean immediate;

		Mode(String name, Item item, boolean immediate)
		{
			this.name = name;
			this.item = item;
			this.immediate = immediate;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
