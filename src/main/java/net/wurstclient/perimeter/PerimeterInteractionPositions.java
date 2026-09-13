/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ported from the reference mod: finds the places a player can stand to reach a
 * facility block, and the exact spot to stand on to drop products down a
 * vertical unloading shaft.
 */
public final class PerimeterInteractionPositions
{
	private final int searchRadius;
	private final double interactionDistanceSquared;
	
	public PerimeterInteractionPositions(int searchRadius,
		double interactionDistanceSquared)
	{
		this.searchRadius = searchRadius;
		this.interactionDistanceSquared = interactionDistanceSquared;
	}
	
	public List<BlockPos> find(Level world, BlockPos target)
	{
		if(world == null)
			return List.of();
		
		List<BlockPos> positions = new ArrayList<>();
		
		for(int dx = -searchRadius; dx <= searchRadius; dx++)
			for(int dz = -searchRadius; dz <= searchRadius; dz++)
			{
				if(dx == 0 && dz == 0)
					continue;
				
				BlockPos position = target.offset(dx, 0, dz);
				
				if(world.getBlockState(position)
					.getBlock() instanceof SlabBlock)
					position = position.above();
				
				if(isValid(world, position, target))
					positions.add(position);
			}
		
		return List.copyOf(positions);
	}
	
	public Optional<BlockPos> closest(Level world, BlockPos target,
		BlockPos origin)
	{
		return find(world, target).stream()
			.min(Comparator.comparingDouble(origin::distSqr));
	}
	
	public boolean isAtValidPosition(Level world, BlockPos target,
		BlockPos playerFeet)
	{
		return find(world, target).contains(playerFeet);
	}
	
	public boolean canReach(Player player, BlockPos target)
	{
		if(player == null)
			return false;
		
		Vec3 eye = player.getEyePosition();
		return eye.distanceToSqr(Vec3.atCenterOf(target)) <= interactionDistanceSquared
			&& canSee(player, eye, target);
	}
	
	private boolean isValid(Level world, BlockPos position, BlockPos target)
	{
		if(!isSafeStandingPosition(world, position))
			return false;
		
		Player player = net.wurstclient.WurstClient.MC.player;
		
		if(player == null)
			return false;
		
		Vec3 eye = new Vec3(position.getX() + 0.5, position.getY() + 1.62,
			position.getZ() + 0.5);
		
		return isSafelyWithinReach(position, target, interactionDistanceSquared)
			&& canSee(player, eye, target);
	}
	
	private static boolean canSee(Player player, Vec3 eye, BlockPos target)
	{
		HitResult hit = player.level().clip(
			new ClipContext(eye, Vec3.atCenterOf(target), ClipContext.Block.OUTLINE,
				ClipContext.Fluid.NONE, player));
		
		return hit.getType() == HitResult.Type.BLOCK
			&& ((BlockHitResult)hit).getBlockPos().equals(target);
	}
	
	static boolean isSafelyWithinReach(BlockPos position, BlockPos target,
		double distanceSquared)
	{
		double dx = Math.abs(position.getX() - target.getX()) + 0.5;
		double dy = position.getY() + 1.62 - (target.getY() + 0.5);
		double dz = Math.abs(position.getZ() - target.getZ()) + 0.5;
		return dx * dx + dy * dy + dz * dz <= distanceSquared;
	}
	
	/**
	 * Ported from the reference: the block below must carry the player, nothing
	 * dangerous may be there, and the feet and head must be free of blocks and
	 * fluids.
	 */
	public static boolean isSafeStandingPosition(Level world, BlockPos position)
	{
		BlockPos floor = position.below();
		BlockPos head = position.above();
		
		if(!world.isLoaded(floor) || !world.isLoaded(head))
			return false;
		
		BlockState floorState = world.getBlockState(floor);
		BlockState feetState = world.getBlockState(position);
		BlockState headState = world.getBlockState(head);
		
		return (floorState.isFaceSturdy(world, floor, Direction.UP)
			|| floorState.getBlock() instanceof SlabBlock)
			&& !floorState.is(Blocks.MAGMA_BLOCK)
			&& !floorState.is(Blocks.CAMPFIRE)
			&& !floorState.is(Blocks.SOUL_CAMPFIRE)
			&& !feetState.is(Blocks.POWDER_SNOW)
			&& !(feetState.getBlock() instanceof BaseFireBlock)
			&& floorState.getFluidState().isEmpty()
			&& feetState.getFluidState().isEmpty()
			&& headState.getFluidState().isEmpty()
			&& feetState.getCollisionShape(world, position).isEmpty()
			&& headState.getCollisionShape(world, head).isEmpty();
	}
	
	/**
	 * Picks the closest safe standing position in a column, searching upwards
	 * first and then downwards from the preferred height, as the reference
	 * does.
	 */
	public static BlockPos closestSafeStandingPosition(Level world, int x, int z,
		int preferredY, int minY, int maxY)
	{
		int centerY = Math.max(minY, Math.min(maxY, preferredY));
		int maxDifference = Math.max(centerY - minY, maxY - centerY);
		
		for(int difference = 0; difference <= maxDifference; difference++)
		{
			BlockPos upper = new BlockPos(x, centerY + difference, z);
			
			if(upper.getY() <= maxY && isSafeStandingPosition(world, upper))
				return upper;
			
			if(difference > 0)
			{
				BlockPos lower = new BlockPos(x, centerY - difference, z);
				
				if(lower.getY() >= minY
					&& isSafeStandingPosition(world, lower))
					return lower;
			}
		}
		
		return null;
	}
	
	/**
	 * Ported from the reference: the standing position is pushed towards the
	 * shaft by half a block plus the configured inset so that the player stands
	 * right on the edge.
	 */
	public static Vec3 unloadEdgePosition(BlockPos standingBlock, int targetX,
		int targetZ, double inset)
	{
		double centerX = standingBlock.getX() + 0.5;
		double centerZ = standingBlock.getZ() + 0.5;
		double dx = targetX + 0.5 - centerX;
		double dz = targetZ + 0.5 - centerZ;
		double distance = Math.sqrt(dx * dx + dz * dz);
		
		if(distance == 0.0)
			return new Vec3(centerX, standingBlock.getY(), centerZ);
		
		double unitX = dx / distance;
		double unitZ = dz / distance;
		double maxOffset = 0.5 + inset;
		double xScale = Math.abs(unitX) < 1.0E-6 ? Double.POSITIVE_INFINITY
			: maxOffset / Math.abs(unitX);
		double zScale = Math.abs(unitZ) < 1.0E-6 ? Double.POSITIVE_INFINITY
			: maxOffset / Math.abs(unitZ);
		double scale = Math.min(xScale, zScale);
		
		return new Vec3(centerX + unitX * scale, standingBlock.getY(),
			centerZ + unitZ * scale);
	}
	
	public static Vec3 centerOf(int x, int y, int z)
	{
		return new Vec3(x + 0.5, y + 0.5, z + 0.5);
	}
}
