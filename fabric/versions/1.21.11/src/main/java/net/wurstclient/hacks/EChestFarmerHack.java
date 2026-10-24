/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 *
 * Ported from Meteor Client (GPL-3.0).
 */
package net.wurstclient.hacks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.ChatUtils;

/**
 * Ported from Meteor Client (GPL-3.0).
 *
 * <p>
 * Looks at the block above the block you are aiming at, places an ender chest
 * there and breaks it again. Using a pickaxe without Silk Touch turns the
 * ender chest into obsidian.
 */
@SearchTags({"echest farmer", "EChestFarmer", "ender chest farmer",
	"EnderChestFarmer", "obsidian farmer", "obsidian bot"})
public final class EChestFarmerHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far away the target block can be.", 4.5, 1, 6, 0.05,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final SliderSetting cooldown = new SliderSetting("Cooldown",
		"How long to wait between placing and breaking.", 1, 1, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private BlockPos target;
	private int timer;

	public EChestFarmerHack()
	{
		super("EChestFarmer");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(cooldown);
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

		if(MC.gameMode != null && MC.level != null && target != null
			&& MC.level.getBlockState(target).getBlock() == Blocks.ENDER_CHEST)
			MC.gameMode.stopDestroyBlock();

		target = null;
		timer = 0;
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null || MC.gameMode == null)
			return;

		if(timer > 0)
		{
			timer--;
			return;
		}

		if(target == null)
		{
			if(!(MC.hitResult instanceof BlockHitResult hitResult)
				|| hitResult.getType() != HitResult.Type.BLOCK)
				return;

			BlockPos pos = hitResult.getBlockPos().above();
			BlockState state = MC.level.getBlockState(pos);
			if(!state.canBeReplaced()
				&& state.getBlock() != Blocks.ENDER_CHEST)
				return;

			target = pos;
		}

		if(MC.player.distanceToSqr(Vec3.atCenterOf(target)) > range
			.getValueSq())
		{
			target = null;
			return;
		}

		BlockState state = MC.level.getBlockState(target);
		if(state.getBlock() == Blocks.ENDER_CHEST)
		{
			WURST.getHax().autoToolHack.equipBestTool(target, false, true, 0);
			BlockBreaker.breakOneBlock(target);
			timer = cooldown.getValueI();
			return;
		}

		if(!state.canBeReplaced())
			return;

		int slot = findEnderChestSlot();
		if(slot == -1)
		{
			ChatUtils.error("No ender chest in the hotbar.");
			setEnabled(false);
			return;
		}

		MC.player.getInventory().setSelectedSlot(slot);
		BlockPlacer.place(target, false, true);
		timer = cooldown.getValueI();
	}

	private int findEnderChestSlot()
	{
		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(stack.is(Items.ENDER_CHEST))
				return i;
		}

		return -1;
	}
}
