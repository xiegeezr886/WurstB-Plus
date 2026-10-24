/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 *
 * Simplified port: this keeps Meteor's combat behaviour of dropping anvils
 * onto a nearby player. On-demand repair through the anvil screen would need
 * dedicated container automation, which is not part of this port.
 */
package net.wurstclient.hacks;

import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.FakePlayerEntity;

@SearchTags({"auto anvil", "AutoAnvil", "anvil aura", "anvil bomb"})
public final class AutoAnvilHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far to search for targets.", 4, 1, 6, 0.1,
		ValueDisplay.DECIMAL);

	private final SliderSetting height = new SliderSetting("Height",
		"How many blocks above the target anvils are placed.", 2, 0, 5, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting delay = new SliderSetting("Delay",
		"Ticks to wait between anvil placements.", 10, 0, 50, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting placeAtFeet = new CheckboxSetting(
		"Place at feet",
		"Places a pressure plate or button at the target's feet to break"
			+ " the anvils.",
		true);

	private final CheckboxSetting multiPlace = new CheckboxSetting(
		"Multi place", "Places multiple anvils at once.", true);

	private final CheckboxSetting toggleOnBreak = new CheckboxSetting(
		"Toggle on break",
		"Disables when the target's helmet slot becomes empty.", false);

	private Player target;
	private int timer;

	public AutoAnvilHack()
	{
		super("AutoAnvil");
		setCategory(Category.COMBAT);
		addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
		addSetting(range);
		addSetting(height);
		addSetting(delay);
		addSetting(placeAtFeet);
		addSetting(multiPlace);
		addSetting(toggleOnBreak);
	}

	@Override
	protected void onEnable()
	{
		target = null;
		timer = 0;
		EVENTS.add(UpdateListener.class, this);
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
		target = null;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.player.isSpectator())
			return;

		if(toggleOnBreak.isChecked() && target != null
			&& target.getItemBySlot(EquipmentSlot.HEAD).isEmpty())
		{
			ChatUtils.error("AutoAnvil: target's helmet broke, disabling.");
			setEnabled(false);
			return;
		}

		if(!isValidTarget(target))
		{
			target = findTarget();
			if(target == null)
				return;
		}

		if(placeAtFeet.isChecked())
			placeFloorBlock(target.blockPosition());

		if(timer < delay.getValueI())
		{
			timer++;
			return;
		}

		timer = 0;

		int anvilSlot = findHotbarItem(stack ->
			Block.byItem(stack.getItem()) instanceof AnvilBlock);
		if(anvilSlot == -1)
			return;

		int prevSlot = MC.player.getInventory().selected;
		MC.player.getInventory().selected = anvilSlot;

		int maxHeight = Math.max(2, height.getValueI());
		for(int i = maxHeight; i >= 2; i--)
		{
			BlockPos pos = target.blockPosition().above(i);
			if(!canPlaceAnvilAt(target.blockPosition(), i))
				continue;

			if(BlockPlacer.place(pos, false, false))
			{
				MC.player.swing(InteractionHand.MAIN_HAND);
				if(!multiPlace.isChecked())
					break;
			}
		}

		MC.player.getInventory().selected = prevSlot;
	}

	private void placeFloorBlock(BlockPos pos)
	{
		int slot = findHotbarItem(stack ->
			Block.byItem(stack.getItem()) instanceof BasePressurePlateBlock
				|| Block.byItem(stack.getItem()) instanceof ButtonBlock);
		if(slot == -1)
			return;

		int prevSlot = MC.player.getInventory().selected;
		MC.player.getInventory().selected = slot;

		if(BlockPlacer.place(pos, false, false))
			MC.player.swing(InteractionHand.MAIN_HAND);

		MC.player.getInventory().selected = prevSlot;
	}

	private boolean canPlaceAnvilAt(BlockPos targetPos, int anvilHeight)
	{
		BlockPos pos = targetPos.above(anvilHeight);
		if(!BlockUtils.getState(pos).canBeReplaced())
			return false;

		// the falling anvil needs a clear path down to the target
		for(int j = 1; j < anvilHeight; j++)
			if(!BlockUtils.getState(targetPos.above(j)).canBeReplaced())
				return false;

		return true;
	}

	private int findHotbarItem(Predicate<ItemStack> predicate)
	{
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(!stack.isEmpty() && predicate.test(stack))
				return i;
		}

		return -1;
	}

	private boolean isValidTarget(Player player)
	{
		return player != null && !player.isRemoved() && player.getHealth() > 0
			&& MC.player.distanceToSqr(player) <= range.getValueSq();
	}

	private Player findTarget()
	{
		double rangeSq = range.getValueSq();

		return StreamSupport
			.stream(MC.level.entitiesForRendering().spliterator(), false)
			.filter(e -> e instanceof Player player && player != MC.player
				&& player.getHealth() > 0)
			.filter(e -> !e.isRemoved())
			.filter(e -> !(e instanceof FakePlayerEntity))
			.filter(e -> !WURST.getFriends().contains(e.getScoreboardName()))
			.filter(e -> MC.player.distanceToSqr(e) <= rangeSq)
			.map(e -> (Player)e)
			.min(Comparator.comparingDouble(Player::getHealth)).orElse(null);
	}
}
