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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockListSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.ChatUtils;
import net.wurstclient.util.RotationUtils;

/**
 * Simplified port of Meteor's SpawnProofer module.
 *
 * <p>
 * Scans the area around the player for positions where mobs could spawn and
 * places the configured blocks there. This port only supports full blocks,
 * slabs, buttons, carpets and similar floor-supported blocks; it does not
 * enumerate every vanilla spawn rule like Meteor does.
 */
@SearchTags({"spawn proofer", "spawnproof", "spawn proof", "mob proof",
	"light up"})
public final class SpawnProoferHack extends Hack implements UpdateListener
{
	private final SliderSetting range = new SliderSetting("Range",
		"How far away from the player blocks can be placed.", 4.5, 1, 6, 0.1,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final SliderSetting interval = new SliderSetting("Interval",
		"Ticks between placement attempts.", 2, 1, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting lightLevel = new SliderSetting("Light level",
		"Only spawn-proof positions with this light level or lower.", 0, 0, 15,
		1, ValueDisplay.INTEGER);

	private final SliderSetting blocksPerTick = new SliderSetting(
		"Blocks per tick", "Maximum number of blocks placed per attempt.", 1, 1,
		4, 1, ValueDisplay.INTEGER);

	private final BlockListSetting blocks = new BlockListSetting("Blocks",
		"Blocks that may be used for spawn proofing.", "minecraft:torch",
		"minecraft:stone_button", "minecraft:stone_slab");

	private int timer;

	public SpawnProoferHack()
	{
		super("SpawnProofer");
		setCategory(Category.BLOCKS);
		addSetting(range);
		addSetting(interval);
		addSetting(lightLevel);
		addSetting(blocksPerTick);
		addSetting(blocks);
	}

	@Override
	protected void onEnable()
	{
		EVENTS.add(UpdateListener.class, this);
		timer = 0;
	}

	@Override
	protected void onDisable()
	{
		EVENTS.remove(UpdateListener.class, this);
	}

	@Override
	public void onUpdate()
	{
		if(MC.player == null || MC.level == null)
			return;

		if(++timer < interval.getValueI())
			return;

		timer = 0;

		if(!isConfiguredBlock(MC.player.getMainHandItem().getItem()))
		{
			int slot = findHotbarBlock();
			if(slot < 0)
			{
				ChatUtils.error(
					"Found none of the chosen blocks in the hotbar.");
				setEnabled(false);
				return;
			}

			MC.player.getInventory().selected = (slot);
			return;
		}

		Vec3 eyes = RotationUtils.getEyesPos();
		double rangeSq = range.getValueSq();
		BlockPos center = MC.player.blockPosition();
		int radius = range.getValueCeil();

		List<BlockPos> candidates = new ArrayList<>();

		for(int dy = -1; dy <= 1; dy++)
			for(int dx = -radius; dx <= radius; dx++)
				for(int dz = -radius; dz <= radius; dz++)
				{
					BlockPos pos = center.offset(dx, dy, dz);

					if(!MC.level.isInWorldBounds(pos))
						continue;

					if(pos.distToCenterSqr(eyes) > rangeSq)
						continue;

					if(!isSpawnable(pos))
						continue;

					candidates.add(pos);
				}

		candidates.sort(
			Comparator.comparingDouble(pos -> pos.distToCenterSqr(eyes)));

		int placed = 0;
		for(BlockPos pos : candidates)
		{
			if(placed >= blocksPerTick.getValueI())
				break;

			if(BlockPlacer.placeOneBlock(pos))
				placed++;
		}
	}

	private boolean isSpawnable(BlockPos pos)
	{
		BlockState state = MC.level.getBlockState(pos);

		if(!state.canBeReplaced() || !state.getFluidState().isEmpty())
			return false;

		BlockState below = MC.level.getBlockState(pos.below());
		if(!below.isFaceSturdy(MC.level, pos.below(), Direction.UP))
			return false;

		if(MC.level.getMaxLocalRawBrightness(pos) > lightLevel.getValueI())
			return false;

		// Don't place a block inside the player.
		return !MC.player.getBoundingBox().intersects(new AABB(pos));
	}

	private int findHotbarBlock()
	{
		for(int slot = 0; slot < 9; slot++)
			if(isConfiguredBlock(
				MC.player.getInventory().getItem(slot).getItem()))
				return slot;

		return -1;
	}

	private boolean isConfiguredBlock(Item item)
	{
		if(item == null)
			return false;

		Block block = Block.byItem(item);

		return block != Blocks.AIR && blocks.contains(block);
	}
}
