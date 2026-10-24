/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EnchantmentUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.Rotation;

@SearchTags({"auto exp", "auto xp", "auto mend", "repair armor", "mending"})
public final class AutoEXPHack extends Hack implements UpdateListener
{
	private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD,
		EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private final EnumSetting<Mode> mode = new EnumSetting<>("Mode",
		"Which items to repair.", Mode.values(), Mode.BOTH);

	private final SliderSetting minThreshold = new SliderSetting(
		"Min threshold", "Durability percentage at which to start repairing.",
		30, 1, 100, 1, ValueDisplay.INTEGER.withSuffix("%"));

	private final SliderSetting maxThreshold = new SliderSetting(
		"Max threshold", "Durability percentage to repair items up to.", 80, 1,
		100, 1, ValueDisplay.INTEGER.withSuffix("%"));

	private final CheckboxSetting onlyGround = new CheckboxSetting(
		"Only on ground", "Only throw when the player is on the ground.", false);

	private EquipmentSlot repairingSlot;
	private boolean repairingHand;

	public AutoEXPHack()
	{
		super("AutoEXP");
		setCategory(Category.COMBAT);
		addSetting(mode);
		addSetting(minThreshold);
		addSetting(maxThreshold);
		addSetting(onlyGround);
	}

	@Override
	protected void onEnable()
	{
		repairingSlot = null;
		repairingHand = false;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		repairingSlot = null;
		repairingHand = false;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;
		if(onlyGround.isChecked() && !MC.player.onGround())
			return;

		ItemStack target = findTarget();
		if(target == null)
			return;

		int slot = InventoryUtils.indexOf(Items.EXPERIENCE_BOTTLE, 9);
		if(slot == -1)
			return;

		InventoryUtils.selectItem(slot);
		if(!MC.player.getMainHandItem().is(Items.EXPERIENCE_BOTTLE))
			return;

		float oldYaw = MC.player.getYRot();
		float oldPitch = MC.player.getXRot();
		Rotation down = new Rotation(oldYaw, 90);
		down.applyToClientPlayer();
		down.sendPlayerLookPacket();
		MC.gameMode.useItem(MC.player, InteractionHand.MAIN_HAND);
		Rotation back = new Rotation(oldYaw, oldPitch);
		back.applyToClientPlayer();
		back.sendPlayerLookPacket();
	}

	private ItemStack findTarget()
	{
		double min = minThreshold.getValue();
		double max = maxThreshold.getValue();

		// continue repairing the current item until it reaches max threshold
		if(repairingSlot != null)
		{
			ItemStack stack = MC.player.getItemBySlot(repairingSlot);
			if(needsRepair(stack, max))
				return stack;
			repairingSlot = null;
		}

		if(repairingHand)
		{
			ItemStack stack = MC.player.getMainHandItem();
			if(needsRepair(stack, max))
				return stack;
			repairingHand = false;
		}

		// otherwise start a new repair once something drops below min
		if(mode.getSelected() != Mode.HANDS)
			for(EquipmentSlot slot : ARMOR_SLOTS)
			{
				ItemStack stack = MC.player.getItemBySlot(slot);
				if(needsRepair(stack, min))
				{
					repairingSlot = slot;
					return stack;
				}
			}

		if(mode.getSelected() != Mode.ARMOR)
		{
			ItemStack stack = MC.player.getMainHandItem();
			if(needsRepair(stack, min))
			{
				repairingHand = true;
				return stack;
			}
		}

		return null;
	}

	private boolean needsRepair(ItemStack stack, double threshold)
	{
		if(stack.isEmpty() || !stack.isDamageableItem())
			return false;
		if(EnchantmentUtils.getLevel(Enchantments.MENDING, stack) <= 0)
			return false;

		double durability = (stack.getMaxDamage() - stack.getDamageValue())
			/ (double)stack.getMaxDamage() * 100;
		return durability <= threshold;
	}

	private enum Mode
	{
		ARMOR("Armor"),
		HANDS("Hands"),
		BOTH("Both");

		private final String name;

		private Mode(String name)
		{
			this.name = name;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
