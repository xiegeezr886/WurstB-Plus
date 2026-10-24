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

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixinterface.IClientPlayerInteractionManager;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.InventoryUtils;
import net.wurstclient.util.Rotation;

@SearchTags({"quiver", "tipped arrow", "self arrow", "Quiver"})
public final class QuiverHack extends Hack implements UpdateListener
{
	private static final int MAIN_ARROW_SLOT = 9;

	private final EnumSetting<EffectType> effect = new EnumSetting<>("Effect",
		"Which effect to shoot yourself with.", EffectType.values(),
		EffectType.STRENGTH);

	private final SliderSetting cooldown = new SliderSetting("Cooldown",
		"How many ticks between shooting effects (19 minimum for NCP).", 10,
		0, 40, 1, ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting checkEffects = new CheckboxSetting(
		"Check effects", "Won't shoot you with effects you already have.",
		true);

	private final CheckboxSetting silentBow = new CheckboxSetting("Silent bow",
		"Takes a bow from your inventory to quiver.", true);

	private final CheckboxSetting chatInfo = new CheckboxSetting("Chat info",
		"Sends info about quiver checks in chat.", false);

	private final CheckboxSetting onlyInHoles = new CheckboxSetting(
		"Only in holes", "Only quiver when you're in a hole.", true);

	private final CheckboxSetting onlyOnGround = new CheckboxSetting(
		"Only on ground", "Only quiver when you're on the ground.", true);

	private final SliderSetting minHealth = new SliderSetting("Min health",
		"How much health you must have to quiver.", 10, 0, 36, 0.5,
		ValueDisplay.DECIMAL);

	private final ArrayList<Integer> arrowSlots = new ArrayList<>();

	private int prevSlot = -1;
	private int timer;
	private boolean arrowsInitialized;

	public QuiverHack()
	{
		super("Quiver");
		setCategory(Category.COMBAT);
		addSetting(effect);
		addSetting(cooldown);
		addSetting(checkEffects);
		addSetting(silentBow);
		addSetting(chatInfo);
		addSetting(onlyInHoles);
		addSetting(onlyOnGround);
		addSetting(minHealth);
	}

	@Override
	protected void onEnable()
	{
		prevSlot = -1;
		timer = 0;
		arrowsInitialized = false;
		arrowSlots.clear();

		MC.options.keyUse.setDown(false);
		MC.gameMode.releaseUsingItem(MC.player);

		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);

		MC.options.keyUse.setDown(false);
		if(MC.player.isUsingItem())
			MC.gameMode.releaseUsingItem(MC.player);

		if(prevSlot != -1)
		{
			MC.player.getInventory().selected = prevSlot;
			prevSlot = -1;
		}

		timer = 0;
		arrowSlots.clear();
	}

	@Override
	public void onUpdate()
	{
		if(!shouldQuiver())
			return;

		if(!MC.player.isHolding(Items.BOW))
		{
			int bowSlot = InventoryUtils.indexOf(Items.BOW,
				silentBow.isChecked() ? 36 : 9);
			if(bowSlot == -1)
			{
				fail("Couldn't find a usable bow, disabling.");
				return;
			}

			if(prevSlot == -1)
				prevSlot = MC.player.getInventory().selected;

			InventoryUtils.selectItem(bowSlot);
			return;
		}

		if(prevSlot == -1)
			prevSlot = MC.player.getInventory().selected;

		if(!arrowsInitialized)
		{
			arrowsInitialized = true;
			findArrows();

			if(arrowSlots.isEmpty())
			{
				fail("Couldn't find a usable arrow, disabling.");
				return;
			}
		}

		if(arrowSlots.isEmpty())
		{
			fail("No more usable arrows, disabling.");
			return;
		}

		if(timer > 0)
		{
			timer--;
			return;
		}

		if(!MC.options.keyUse.isDown())
		{
			moveToMainSlot(arrowSlots.get(0));
			MC.options.keyUse.setDown(true);
			return;
		}

		if(!MC.player.isUsingItem())
			return;

		// 4 ticks of charge is enough for the arrow to actually fire
		int chargeTicks = 72000 - MC.player.getUseItemRemainingTicks();
		if(chargeTicks < 4)
			return;

		arrowSlots.remove(0);

		new Rotation(MC.player.getYRot(), -90).sendPlayerLookPacket();
		MC.options.keyUse.setDown(false);
		MC.gameMode.releaseUsingItem(MC.player);
		new Rotation(MC.player.getYRot(), MC.player.getXRot())
			.sendPlayerLookPacket();

		timer = cooldown.getValueI();
	}

	private boolean shouldQuiver()
	{
		int bowSlot = InventoryUtils.indexOf(Items.BOW,
			silentBow.isChecked() ? 36 : 9);
		if(bowSlot == -1)
		{
			fail("Couldn't find a usable bow, disabling.");
			return false;
		}

		if(!headIsOpen())
		{
			fail("Not enough space to quiver, disabling.");
			return false;
		}

		if(MC.player.getHealth()
			+ MC.player.getAbsorptionAmount() < minHealth.getValueF())
		{
			fail("Not enough health to quiver, disabling.");
			return false;
		}

		if(onlyOnGround.isChecked() && !MC.player.onGround())
		{
			fail("You are not on the ground, disabling.");
			return false;
		}

		if(onlyInHoles.isChecked() && !isSurrounded())
		{
			fail("You are not in a hole, disabling.");
			return false;
		}

		return true;
	}

	private void findArrows()
	{
		arrowSlots.clear();
		MobEffect wanted = effect.getSelected().effect;

		if(checkEffects.isChecked() && hasEffect(wanted))
			return;

		for(int i = 0; i < 36; i++)
		{
			if(i == MC.player.getInventory().selected)
				continue;

			ItemStack stack = MC.player.getInventory().getItem(i);
			if(!stack.is(Items.TIPPED_ARROW))
				continue;

			for(MobEffectInstance instance : PotionUtils
				.getMobEffects(stack))
				if(instance.getEffect().equals(wanted))
				{
					arrowSlots.add(i);
					break;
				}
		}
	}

	private void moveToMainSlot(int slot)
	{
		if(slot == MAIN_ARROW_SLOT)
			return;

		IClientPlayerInteractionManager im =
			(IClientPlayerInteractionManager)IMC.getInteractionManager();

		int from = InventoryUtils.toNetworkSlot(slot);
		int to = InventoryUtils.toNetworkSlot(MAIN_ARROW_SLOT);

		im.windowClick_PICKUP(from);
		im.windowClick_PICKUP(to);

		if(!MC.player.inventoryMenu.getCarried().isEmpty())
			im.windowClick_PICKUP(from);
	}

	private boolean hasEffect(MobEffect effect)
	{
		for(MobEffectInstance instance : MC.player.getActiveEffects())
			if(instance.getEffect().equals(effect))
				return true;

		return false;
	}

	private boolean headIsOpen()
	{
		BlockPos pos = MC.player.blockPosition().above();
		if(BlockUtils.getState(pos).isSolid())
			return false;

		return !BlockUtils.getState(pos.above()).isSolid();
	}

	private boolean isSurrounded()
	{
		BlockPos pos = MC.player.blockPosition();

		for(Direction dir : Direction.values())
		{
			if(dir == Direction.UP || dir == Direction.DOWN)
				continue;

			Block block = BlockUtils.getBlock(pos.relative(dir));

			if(block != Blocks.OBSIDIAN && block != Blocks.BEDROCK
				&& block != Blocks.RESPAWN_ANCHOR
				&& block != Blocks.CRYING_OBSIDIAN
				&& block != Blocks.NETHERITE_BLOCK)
				return false;
		}

		return true;
	}

	private void fail(String message)
	{
		if(chatInfo.isChecked())
			ChatUtils.error(message);

		setEnabled(false);
	}

	private enum EffectType
	{
		STRENGTH("Strength", MobEffects.DAMAGE_BOOST),
		SPEED("Speed", MobEffects.MOVEMENT_SPEED),
		JUMP_BOOST("Jump Boost", MobEffects.JUMP),
		REGENERATION("Regeneration", MobEffects.REGENERATION),
		RESISTANCE("Resistance", MobEffects.DAMAGE_RESISTANCE),
		FIRE_RESISTANCE("Fire Resistance", MobEffects.FIRE_RESISTANCE),
		WATER_BREATHING("Water Breathing", MobEffects.WATER_BREATHING),
		NIGHT_VISION("Night Vision", MobEffects.NIGHT_VISION),
		INVISIBILITY("Invisibility", MobEffects.INVISIBILITY),
		SLOW_FALLING("Slow Falling", MobEffects.SLOW_FALLING),
		HASTE("Haste", MobEffects.DIG_SPEED),
		ABSORPTION("Absorption", MobEffects.ABSORPTION);

		private final String name;
		private final MobEffect effect;

		private EffectType(String name, MobEffect effect)
		{
			this.name = name;
			this.effect = effect;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
