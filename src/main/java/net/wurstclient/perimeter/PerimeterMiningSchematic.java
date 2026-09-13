/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import baritone.api.schematic.AbstractSchematic;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The mining schematic handed to Baritone's builder, ported from the reference
 * mod so that the dig itself, the pathing and the liquid policies behave the
 * same way they do there.
 *
 * <p>
 * Under {@code seal_boundary} the schematic grows by one block to all four
 * sides and by one layer on top, so that fluid blocks touching the sides or the
 * top of the area become part of the schematic and are sealed while the area is
 * excavated.
 */
public final class PerimeterMiningSchematic extends AbstractSchematic
{
	private final PerimeterRegion area;
	private final PerimeterLiquidPolicy policy;
	private final List<Block> sealingBlocks;
	private final Set<Block> disallowed;
	private final PerimeterFluidLookup fluids;
	private final BlockPos origin;
	
	public PerimeterMiningSchematic(PerimeterRegion area,
		PerimeterLiquidPolicy policy, Collection<Block> sealingBlocks,
		Collection<Block> disallowed, PerimeterFluidLookup fluids)
	{
		super(width(area, policy), height(area, policy), length(area, policy));
		this.area = Objects.requireNonNull(area, "area");
		this.policy = Objects.requireNonNull(policy, "policy");
		this.sealingBlocks = List.copyOf(sealingBlocks);
		this.disallowed = new HashSet<>(disallowed);
		this.fluids = fluids;
		this.origin = new BlockPos(area.minX() - inset(policy), area.minY(),
			area.minZ() - inset(policy));
	}
	
	public BlockPos origin()
	{
		return origin;
	}
	
	@Override
	public boolean inSchematic(int x, int y, int z, BlockState currentState)
	{
		if(!super.inSchematic(x, y, z, currentState))
			return false;
		
		int worldX = x + origin.getX();
		int worldY = y + origin.getY();
		int worldZ = z + origin.getZ();
		boolean currentIsFluid =
			currentState != null && !currentState.getFluidState().isEmpty();
		boolean hasSealingBlocks = !sealingBlocks.isEmpty();
		
		if(area.contains(worldX, worldY, worldZ))
		{
			if(currentState != null
				&& disallowed.contains(currentState.getBlock()))
				return false;
			
			boolean fluidAdjacent = fluids == null
				|| fluids.isFluidAdjacent(worldX, worldY, worldZ);
			
			return PerimeterFluidGuard.processInside(policy, hasSealingBlocks,
				currentIsFluid, fluidAdjacent);
		}
		
		return PerimeterFluidGuard.processRim(area, policy, hasSealingBlocks,
			worldX, worldY, worldZ, currentIsFluid);
	}
	
	@Override
	public BlockState desiredState(int x, int y, int z, BlockState current,
		List<BlockState> approxPlaceable)
	{
		int worldX = x + origin.getX();
		int worldY = y + origin.getY();
		int worldZ = z + origin.getZ();
		
		if(area.contains(worldX, worldY, worldZ))
		{
			boolean currentIsFluid =
				current != null && !current.getFluidState().isEmpty();
			
			if(PerimeterFluidGuard.replaceFluidInside(policy, currentIsFluid))
				return sealingState(approxPlaceable);
			
			return Blocks.AIR.defaultBlockState();
		}
		
		return sealingState(approxPlaceable);
	}
	
	private BlockState sealingState(List<BlockState> approxPlaceable)
	{
		for(BlockState state : approxPlaceable)
			if(sealingBlocks.contains(state.getBlock()))
				return state;
		
		if(sealingBlocks.isEmpty())
			return Blocks.AIR.defaultBlockState();
		
		return sealingBlocks.get(0).defaultBlockState();
	}
	
	private static int inset(PerimeterLiquidPolicy policy)
	{
		return policy == PerimeterLiquidPolicy.SEAL_BOUNDARY ? 1 : 0;
	}
	
	private static int width(PerimeterRegion area, PerimeterLiquidPolicy policy)
	{
		return checked((long)area.maxX() - area.minX() + 1L
			+ 2L * inset(policy));
	}
	
	private static int height(PerimeterRegion area, PerimeterLiquidPolicy policy)
	{
		return checked(
			(long)area.maxY() - area.minY() + 1L + inset(policy));
	}
	
	private static int length(PerimeterRegion area, PerimeterLiquidPolicy policy)
	{
		return checked((long)area.maxZ() - area.minZ() + 1L
			+ 2L * inset(policy));
	}
	
	private static int checked(long value)
	{
		if(value <= 0L || value > Integer.MAX_VALUE)
			throw new IllegalArgumentException(
				"Area dimension is outside the supported integer range: "
					+ value);
		
		return (int)value;
	}
}
