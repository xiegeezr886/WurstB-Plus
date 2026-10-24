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

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.EnchantmentUtils;

@SearchTags({"auto weapon", "best weapon", "weapon swap", "AutoWeapon"})
public final class AutoWeaponHack extends Hack
	implements PlayerAttacksEntityListener
{
	private final EnumSetting<Weapon> weapon = new EnumSetting<>("Weapon",
		"What type of weapon to use.", Weapon.values(), Weapon.SWORD);

	private final SliderSetting threshold = new SliderSetting("Threshold",
		"If the non-preferred weapon produces this much more damage,"
			+ " this will favor it over your preferred weapon.",
		4, 0, 20, 1, ValueDisplay.DECIMAL);

	private final CheckboxSetting antiBreak = new CheckboxSetting("Anti break",
		"Prevents you from breaking your weapon.", false);

	public AutoWeaponHack()
	{
		super("AutoWeapon");
		setCategory(Category.COMBAT);
		addSetting(weapon);
		addSetting(threshold);
		addSetting(antiBreak);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(PlayerAttacksEntityListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(PlayerAttacksEntityListener.class, this);
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		if(!(target instanceof LivingEntity living))
			return;

		int slot = getBestWeapon(living);
		if(slot >= 0)
			MC.player.getInventory().selected = slot;
	}

	private int getBestWeapon(LivingEntity target)
	{
		int slotS = MC.player.getInventory().selected;
		int slotA = MC.player.getInventory().selected;
		double damageS = 0;
		double damageA = 0;

		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.isEmpty())
				continue;

			if(stack.is(ItemTags.SWORDS))
			{
				if(antiBreak.isChecked() && isAboutToBreak(stack))
					continue;

				double damage = getAttackDamage(stack);
				if(damage > damageS)
				{
					damageS = damage;
					slotS = i;
				}

			}else if(stack.is(ItemTags.AXES))
			{
				if(antiBreak.isChecked() && isAboutToBreak(stack))
					continue;

				double damage = getAttackDamage(stack);
				if(damage > damageA)
				{
					damageA = damage;
					slotA = i;
				}
			}
		}

		int thresholdI = threshold.getValueI();

		if(weapon.getSelected() == Weapon.SWORD)
		{
			if(thresholdI > damageA - damageS)
				return slotS;
			if(thresholdI < damageA - damageS)
				return slotA;
		}else
		{
			if(thresholdI > damageS - damageA)
				return slotA;
			if(thresholdI < damageS - damageA)
				return slotS;
		}

		return MC.player.getInventory().selected;
	}

	private boolean isAboutToBreak(ItemStack stack)
	{
		if(!stack.isDamageableItem())
			return false;

		return stack.getMaxDamage() - stack.getDamageValue() <= 10;
	}

	private double getAttackDamage(ItemStack stack)
	{
		double damage = 1;

		double[] bonus = {0};
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			if(attribute.equals(Attributes.ATTACK_DAMAGE))
				bonus[0] += modifier.amount();
		});
		damage += bonus[0];

		int sharpness =
			EnchantmentUtils.getLevel(Enchantments.SHARPNESS, stack);
		if(sharpness > 0)
			damage += 1 + 0.5 * (sharpness - 1);

		return damage;
	}

	private enum Weapon
	{
		SWORD("Sword"),
		AXE("Axe");

		private final String name;

		private Weapon(String name)
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
