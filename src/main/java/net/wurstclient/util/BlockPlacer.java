/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.IMinecraftClient;
import net.wurstclient.util.BlockBreaker.BlockBreakingParams;

public enum BlockPlacer
{
	;
	
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final Minecraft MC = WurstClient.MC;
	private static final IMinecraftClient IMC = WurstClient.IMC;
	
	public static boolean placeOneBlock(BlockPos pos)
	{
		BlockPlacingParams params = getBlockPlacingParams(pos);
		if(params == null)
			return false;
		
		// face block
		WURST.getRotationFaker().faceVectorPacket(params.hitVec);
		
		// place block
		IMC.getInteractionManager().rightClickBlock(params.neighbor,
			params.side, params.hitVec);
		
		return true;
	}

	public static boolean place(BlockPos pos, boolean airPlace,
		boolean checkLOS)
	{
		return place(pos, airPlace, checkLOS, null);
	}

	public static boolean place(BlockPos pos, boolean airPlace,
		boolean checkLOS, RotationQueue rotationQueue)
	{
		if(!MC.level.isInWorldBounds(pos)
			|| !BlockUtils.getState(pos).canBeReplaced())
			return false;

		for(Direction d : Direction.values())
		{
			BlockPos neighbor = pos.relative(d);

			if(!airPlace
				&& BlockUtils.getState(neighbor).canBeReplaced())
				continue;

			Vec3 hitVec = getLegitLookPos(neighbor, d.getOpposite(), checkLOS, 5);

			if(hitVec == null)
			{
				if(checkLOS)
					continue;

				hitVec = getLegitLookPos(neighbor, d.getOpposite(), false, 5);
				if(hitVec == null)
					continue;
			}

			if(rotationQueue != null)
				rotationQueue.setRotation(
					RotationUtils.getNeededRotations(hitVec));
			else
				WURST.getRotationFaker().faceVectorPacket(hitVec);

			IMC.getInteractionManager().rightClickBlock(neighbor,
				d.getOpposite(), hitVec);
			return true;
		}

		if(airPlace)
		{
			for(Direction d : Direction.values())
			{
				Vec3 hitVec = getLegitLookPos(pos, d, checkLOS, 5);
				if(hitVec == null)
					continue;

				if(rotationQueue != null)
					rotationQueue.setRotation(
						RotationUtils.getNeededRotations(hitVec));
				else
					WURST.getRotationFaker().faceVectorPacket(hitVec);

				IMC.getInteractionManager().rightClickBlock(pos, d, hitVec);
				return true;
			}
		}

		return false;
	}

	public static Vec3 getLegitLookPos(BlockPos pos, Direction dir,
		boolean raycast, int res)
	{
		return getLegitLookPos(new AABB(pos), dir, raycast, res, 0.01);
	}

	public static Vec3 getLegitLookPos(AABB box, Direction dir,
		boolean raycast, int res, double extrude)
	{
		Vec3 eyePos = RotationUtils.getEyesPos();
		Vec3 blockPos = new Vec3(box.minX, box.minY, box.minZ).add(
			dir == Direction.WEST ? -extrude
				: dir.getStepX() * (box.maxX - box.minX) + extrude,
			dir == Direction.DOWN ? -extrude
				: dir.getStepY() * (box.maxY - box.minY) + extrude,
			dir == Direction.NORTH ? -extrude
				: dir.getStepZ() * (box.maxZ - box.minZ) + extrude);

		for(double i = 0; i <= 1; i += 1.0 / res)
			for(double j = 0; j <= 1; j += 1.0 / res)
			{
				Direction.Axis axis = dir.getAxis();
				Vec3 lookPos = blockPos.add(
					axis == Direction.Axis.X ? 0
						: i * (box.maxX - box.minX),
					axis == Direction.Axis.Y ? 0
						: axis == Direction.Axis.Z
							? j * (box.maxY - box.minY)
							: i * (box.maxY - box.minY),
					axis == Direction.Axis.Z ? 0
						: j * (box.maxZ - box.minZ));

				if(eyePos.distanceTo(lookPos) > 4.55)
					continue;

				if(!raycast)
					return lookPos;

				if(!BlockUtils.hasLineOfSight(eyePos, lookPos))
					continue;

				return lookPos;
			}

		return null;
	}
	
	/**
	 * Returns everything you need to place a block at the given position, such
	 * as the position of the block to place against (can be a neighbor or the
	 * block itself), the side of that block to place on, the hit vector, the
	 * squared distance to that hit vector, and whether there is line of sight
	 * to that hit vector.
	 */
	public static BlockPlacingParams getBlockPlacingParams(BlockPos pos)
	{
		// if there is a replaceable block at the position, we need to place
		// against the block itself instead of a neighbor
		if(BlockUtils.canBeClicked(pos)
			&& BlockUtils.getState(pos).canBeReplaced())
		{
			// the parameters for this happen to be the same as for breaking
			// the block, so we can just use BlockBreaker to get them
			BlockBreakingParams breakParams =
				BlockBreaker.getBlockBreakingParams(pos);
			
			// should never happen, but just in case
			if(breakParams == null)
				return null;
			
			return new BlockPlacingParams(pos, breakParams.side(),
				breakParams.hitVec(), breakParams.distanceSq(),
				breakParams.lineOfSight());
		}
		
		Direction[] sides = Direction.values();
		Vec3[] hitVecs = new Vec3[sides.length];
		
		// get hit vectors for all usable sides
		for(int i = 0; i < sides.length; i++)
		{
			BlockPos neighbor = pos.relative(sides[i]);
			BlockState state = BlockUtils.getState(neighbor);
			VoxelShape shape = state.getShape(MC.level, neighbor);
			
			// if neighbor has no shape or is replaceable, it can't be used
			if(shape.isEmpty() || state.canBeReplaced())
				continue;
			
			AABB box = shape.bounds();
			Vec3 halfSize = new Vec3(box.maxX - box.minX, box.maxY - box.minY,
				box.maxZ - box.minZ).scale(0.5);
			Vec3 center = Vec3.atLowerCornerOf(neighbor).add(box.getCenter());
			
			Vec3i dirVec = sides[i].getOpposite().getNormal();
			Vec3 relHitVec = new Vec3(halfSize.x * dirVec.getX(),
				halfSize.y * dirVec.getY(), halfSize.z * dirVec.getZ());
			hitVecs[i] = center.add(relHitVec);
		}
		
		Vec3 eyesPos = RotationUtils.getEyesPos();
		Vec3 posVec = Vec3.atCenterOf(pos);
		
		double distanceSqToPosVec = eyesPos.distanceToSqr(posVec);
		double[] distancesSq = new double[sides.length];
		boolean[] linesOfSight = new boolean[sides.length];
		
		// calculate distances and line of sight
		for(int i = 0; i < sides.length; i++)
		{
			// skip unusable sides
			if(hitVecs[i] == null)
			{
				distancesSq[i] = Double.MAX_VALUE;
				continue;
			}
			
			distancesSq[i] = eyesPos.distanceToSqr(hitVecs[i]);
			
			// to place against a neighbor in front of the block, we would
			// have to place against that neighbor's rear face, which can't
			// possibly have line of sight
			if(distancesSq[i] <= distanceSqToPosVec)
				continue;
			
			linesOfSight[i] = BlockUtils.hasLineOfSight(eyesPos, hitVecs[i]);
		}
		
		// decide which side to use
		Direction side = sides[0];
		for(int i = 1; i < sides.length; i++)
		{
			int bestSide = side.ordinal();
			
			// skip unusable sides
			if(hitVecs[i] == null)
				continue;
			
			// prefer sides with LOS
			if(!linesOfSight[bestSide] && linesOfSight[i])
			{
				side = sides[i];
				continue;
			}
			
			if(linesOfSight[bestSide] && !linesOfSight[i])
				continue;
			
			// then pick the furthest side
			if(distancesSq[i] > distancesSq[bestSide])
				side = sides[i];
		}
		
		// if no usable side was found, return null
		if(hitVecs[side.ordinal()] == null)
			return null;
		
		return new BlockPlacingParams(pos.relative(side), side.getOpposite(),
			hitVecs[side.ordinal()], distancesSq[side.ordinal()],
			linesOfSight[side.ordinal()]);
	}
	
	public static record BlockPlacingParams(BlockPos neighbor, Direction side,
		Vec3 hitVec, double distanceSq, boolean lineOfSight)
	{
		public BlockHitResult toHitResult()
		{
			return new BlockHitResult(hitVec, side, neighbor, false);
		}
	}
}
