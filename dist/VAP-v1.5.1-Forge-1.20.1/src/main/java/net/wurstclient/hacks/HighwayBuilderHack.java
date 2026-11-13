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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.hack.HackConflictGroup;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BaritoneUtils;
import net.wurstclient.util.BlockBreaker;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockUtils;
import net.wurstclient.util.ChatUtils;

/**
 * Simplified port of Meteor's HighwayBuilder module.
 *
 * <p>
 * This builds a 3-wide, 3-high tunnel around the player: it clears the tunnel
 * and paves the floor in a chosen cardinal direction. Unlike Meteor's version
 * it does not manage inventories, shulkers, elytra or crystal traps.
 *
 * <p>
 * Limits: the builder only works in the chunks around the player, so the
 * player has to move forward (manually or with the optional Baritone walk
 * helper). Floors are placed against existing neighbours, so bridging over
 * wide gaps is not supported. The highway has a fixed width of 3 blocks.
 */
@SearchTags({"highway builder", "highway", "tunnel builder", "baritone highway",
	"road builder"})
public final class HighwayBuilderHack extends Hack implements UpdateListener
{
	private final EnumSetting<Heading> heading =
		new EnumSetting<>("Direction",
			"The cardinal direction in which the highway is built.",
			Heading.values(), Heading.NORTH);

	private final BlockListSetting floorBlocks = new BlockListSetting(
		"Floor blocks", "Blocks that may be used to pave the highway floor.",
		"minecraft:obsidian", "minecraft:netherrack");

	private final SliderSetting reach = new SliderSetting("Reach",
		"How many blocks ahead of the player to build.", 3, 1, 5, 1,
		ValueDisplay.INTEGER);

	private final SliderSetting breakDelay = new SliderSetting("Break delay",
		"Ticks between breaking blocks.", 1, 0, 10, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting placeDelay = new SliderSetting("Place delay",
		"Ticks between placing floor blocks.", 1, 0, 10, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final CheckboxSetting baritoneWalk = new CheckboxSetting(
		"Baritone walk", "Uses Baritone to walk forward while building.\n\n"
			+ "Baritone only moves the player, it does not build anything.",
		false);

	private int breakTimer;
	private int placeTimer;
	private int walkTimer;
	private boolean startedBaritone;

	public HighwayBuilderHack()
	{
		super("HighwayBuilder");
		setCategory(Category.BLOCKS);
		addConflictGroup(HackConflictGroup.BLOCK_BREAKING_AUTOMATION);
		addSetting(heading);
		addSetting(floorBlocks);
		addSetting(reach);
		addSetting(breakDelay);
		addSetting(placeDelay);
		addSetting(baritoneWalk);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		breakTimer = 0;
		placeTimer = 0;
		walkTimer = 0;
		startedBaritone = false;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);

		if(startedBaritone && BaritoneUtils.IS_AVAILABLE)
			BaritoneUtils.stop();
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		Heading h = heading.getSelected();

		if(baritoneWalk.isChecked())
			updateBaritone(h);

		BlockPos feet = MC.player.blockPosition();
		int forwardReach = reach.getValueI();
		boolean brokeBlock = false;
		boolean placedBlock = false;

		breakTimer++;
		placeTimer++;

		for(int forward = 0; forward <= forwardReach; forward++)
		{
			int centerX = feet.getX() + h.dx * forward;
			int centerZ = feet.getZ() + h.dz * forward;

			for(int lateral = -1; lateral <= 1; lateral++)
			{
				int x = centerX + h.px * lateral;
				int z = centerZ + h.pz * lateral;

				// Pave the floor.
				if(!placedBlock && placeTimer > placeDelay.getValueI())
				{
					BlockPos floor = new BlockPos(x, feet.getY() - 1, z);

					if(needsFloor(floor))
					{
						if(!selectFloorBlock())
						{
							ChatUtils.error(
								"No floor blocks found in hotbar.");
							setEnabled(false);
							return;
						}

						if(BlockPlacer.placeOneBlock(floor))
						{
							placedBlock = true;
							placeTimer = 0;
						}
					}
				}

				// Clear the tunnel.
				if(brokeBlock || breakTimer <= breakDelay.getValueI())
					continue;

				for(int dy = 0; dy < 3; dy++)
				{
					BlockPos pos = new BlockPos(x, feet.getY() + dy, z);

					if(!canBreak(pos))
						continue;

					if(BlockBreaker.breakOneBlock(pos))
					{
						brokeBlock = true;
						breakTimer = 0;
						break;
					}
				}
			}
		}
	}

	private void updateBaritone(Heading h)
	{
		if(!BaritoneUtils.IS_AVAILABLE)
		{
			ChatUtils.error("Baritone is not available.");
			baritoneWalk.setChecked(false);
			return;
		}

		walkTimer++;
		if(walkTimer < 40)
			return;

		walkTimer = 0;

		if(!BaritoneUtils.isPathing())
		{
			BaritoneUtils.walkDirection(h.yaw, 200);
			startedBaritone = true;
		}
	}

	private boolean needsFloor(BlockPos pos)
	{
		if(!MC.level.isInWorldBounds(pos))
			return false;

		BlockState state = MC.level.getBlockState(pos);
		return state.canBeReplaced() && state.getFluidState().isEmpty();
	}

	private boolean canBreak(BlockPos pos)
	{
		if(!MC.level.isInWorldBounds(pos))
			return false;

		BlockState state = MC.level.getBlockState(pos);

		return !state.isAir() && state.getFluidState().isEmpty()
			&& BlockUtils.canBeClicked(pos) && !BlockUtils.isUnbreakable(pos);
	}

	private boolean selectFloorBlock()
	{
		for(int slot = 0; slot < 9; slot++)
		{
			ItemStack stack = MC.player.getInventory().getItem(slot);
			if(stack.isEmpty())
				continue;

			Block block = Block.byItem(stack.getItem());
			if(block == Blocks.AIR || !floorBlocks.contains(block))
				continue;

			MC.player.getInventory().selected = slot;
			return true;
		}

		return false;
	}

	@Override
	public String getRenderName()
	{
		return isEnabled() ? getName() + " [" + heading.getSelected() + "]"
			: getName();
	}

	private enum Heading
	{
		NORTH("North", 0, -1, 180),
		SOUTH("South", 0, 1, 0),
		EAST("East", 1, 0, -90),
		WEST("West", -1, 0, 90);

		private final String name;
		private final int dx;
		private final int dz;
		private final int px;
		private final int pz;
		private final float yaw;

		Heading(String name, int dx, int dz, float yaw)
		{
			this.name = name;
			this.dx = dx;
			this.dz = dz;
			this.px = -dz;
			this.pz = dx;
			this.yaw = yaw;
		}

		@Override
		public String toString()
		{
			return name;
		}
	}
}
