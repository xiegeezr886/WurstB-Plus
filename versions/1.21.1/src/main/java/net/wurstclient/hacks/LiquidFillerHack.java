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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.BlockSetting;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.BlockPlacer;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;
import net.wurstclient.util.BlockUtils;

/**
 * Ported from Meteor Client (GPL-3.0).
 *
 * <p>
 * Only fills source blocks, not flowing liquid. The configured block must be
 * in the hotbar.
 */
@SearchTags({"liquid filler", "LiquidFiller", "lava filler", "water filler",
	"fluid filler", "fill liquids"})
public final class LiquidFillerHack extends Hack implements UpdateListener
{
	private final BlockSetting block = new BlockSetting("Block",
		"The block to place into liquids.", "minecraft:cobblestone", false);

	private final EnumSetting<LiquidType> liquid = new EnumSetting<>("Liquid",
		"Which liquids to fill.", LiquidType.values(), LiquidType.BOTH);

	private final SliderSetting range = new SliderSetting("Range",
		"How far away liquid source blocks can be filled.", 4.5, 1, 6, 0.05,
		ValueDisplay.DECIMAL.withSuffix(" blocks"));

	private final SliderSetting delay = new SliderSetting("Delay",
		"How long to wait between placement actions.", 2, 0, 20, 1,
		ValueDisplay.INTEGER.withSuffix(" ticks"));

	private final SliderSetting maxBlocksPerTick = new SliderSetting(
		"Max blocks per tick", "The maximum number of blocks to place in one"
			+ " action.",
		1, 1, 10, 1, ValueDisplay.INTEGER);

	private final CheckboxSetting rotate = new CheckboxSetting("Rotate",
		"Faces the block before placing.", true);

	private int timer;

	public LiquidFillerHack()
	{
		super("LiquidFiller");
		setCategory(Category.BLOCKS);
		addSetting(block);
		addSetting(liquid);
		addSetting(range);
		addSetting(delay);
		addSetting(maxBlocksPerTick);
		addSetting(rotate);
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

		int slot = findBlockSlot();
		if(slot == -1)
			return;

		MC.player.getInventory().selected = (slot);

		double rangeSq = range.getValueSq();
		BlockPos center = BlockPos.containing(MC.player.position());
		List<BlockPos> candidates = new ArrayList<>();
		for(BlockPos pos : BlockUtils
			.getAllInBoxStream(center, range.getValueCeil()).toList())
		{
			if(pos.distToCenterSqr(MC.player.position()) > rangeSq)
				continue;
			if(!isFillable(pos))
				continue;

			candidates.add(pos);
		}

		candidates.sort(Comparator
			.comparingDouble(pos -> pos.distToCenterSqr(MC.player.position())));

		int placed = 0;
		double reachSq = 4.55 * 4.55;
		for(BlockPos pos : candidates)
		{
			BlockPlacingParams params =
				BlockPlacer.getBlockPlacingParams(pos);
			if(params == null || params.distanceSq() > reachSq)
				continue;

			if(rotate.isChecked())
				WURST.getRotationFaker().faceVectorPacket(params.hitVec());

			IMC.getInteractionManager().rightClickBlock(params.neighbor(),
				params.side(), params.hitVec());
			MC.rightClickDelay = 4;

			if(++placed >= maxBlocksPerTick.getValueI())
				break;
		}

		if(placed > 0)
			timer = delay.getValueI();
	}

	private boolean isFillable(BlockPos pos)
	{
		BlockState state = BlockUtils.getState(pos);
		FluidState fluidState = state.getFluidState();
		if(!fluidState.isSource())
			return false;
		if(!matchesFluid(fluidState.getType()))
			return false;

		return state.canBeReplaced();
	}

	private boolean matchesFluid(Fluid fluid)
	{
		return switch(liquid.getSelected())
		{
			case WATER -> fluid == Fluids.WATER;
			case LAVA -> fluid == Fluids.LAVA;
			case BOTH -> fluid == Fluids.WATER || fluid == Fluids.LAVA;
		};
	}

	private int findBlockSlot()
	{
		Block wanted = block.getBlock();

		for(int i = 0; i < 9; i++)
		{
			ItemStack stack = MC.player.getInventory().getItem(i);
			if(!stack.isEmpty() && Block.byItem(stack.getItem()) == wanted)
				return i;
		}

		return -1;
	}

	private enum LiquidType
	{
		BOTH("Water and lava"),
		WATER("Water"),
		LAVA("Lava");

		private final String name;

		private LiquidType(String name)
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
