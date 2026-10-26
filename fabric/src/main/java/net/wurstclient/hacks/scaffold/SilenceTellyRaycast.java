/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hacks.scaffold;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.wurstclient.WurstClient;
import net.wurstclient.util.Rotation;

/**
 * OpenOpal's {@code SilenceTellyRaycastUtility}: a voxel-precise DDA raycast
 * that ignores plants/fluids and treats still water as a full cube.
 */
final class SilenceTellyRaycast
{
	private static final double EPSILON = 1.0E-7D;
	private static final double REACH = 4.5D;

	private SilenceTellyRaycast()
	{}

	static boolean didHitBlockFace(Vec3 eyePos, Rotation rotation,
		BlockPos targetPos, Direction expectedFace, boolean strict)
	{
		if(eyePos == null || rotation == null || targetPos == null
			|| expectedFace == null)
			return false;
		BlockHitResult result = getFacedBlock(eyePos, rotation);
		return result != null && targetPos.equals(result.getBlockPos())
			&& (!strict || expectedFace == result.getDirection());
	}

	static BlockHitResult getFacedBlock(Vec3 eyePos, Rotation rotation)
	{
		if(WurstClient.MC.player == null || WurstClient.MC.level == null
			|| eyePos == null || rotation == null)
			return null;

		Vec3 direction = ScaffoldUtils.rotationVector(rotation.pitch(),
			rotation.yaw());
		Vec3 endPos = eyePos.add(direction.scale(REACH));
		if(direction.x == 0.0D)
			direction = new Vec3(EPSILON, direction.y, direction.z);
		if(direction.y == 0.0D)
			direction = new Vec3(direction.x, EPSILON, direction.z);
		if(direction.z == 0.0D)
			direction = new Vec3(direction.x, direction.y, EPSILON);

		BlockPos currentPos = BlockPos.containing(eyePos);
		int stepX = (int)Math.signum(direction.x);
		int stepY = (int)Math.signum(direction.y);
		int stepZ = (int)Math.signum(direction.z);

		double nextBoundaryX = stepX > 0 ? currentPos.getX() + 1.0D
			: currentPos.getX();
		double nextBoundaryY = stepY > 0 ? currentPos.getY() + 1.0D
			: currentPos.getY();
		double nextBoundaryZ = stepZ > 0 ? currentPos.getZ() + 1.0D
			: currentPos.getZ();

		double tMaxX = (nextBoundaryX - eyePos.x) / direction.x;
		double tMaxY = (nextBoundaryY - eyePos.y) / direction.y;
		double tMaxZ = (nextBoundaryZ - eyePos.z) / direction.z;
		double tDeltaX = stepX / direction.x;
		double tDeltaY = stepY / direction.y;
		double tDeltaZ = stepZ / direction.z;
		int maxSteps = Math.max(16, (int)Math.ceil(REACH * 6.0D));

		for(int steps = 0; steps < maxSteps
			&& eyePos.distanceTo(Vec3.atCenterOf(currentPos)) <= REACH + 1.0D;
			steps++)
		{
			BlockState state = WurstClient.MC.level.getBlockState(currentPos);
			if(!isIgnoredBlock(state))
			{
				VoxelShape shape = getShape(state, currentPos);
				if(!shape.isEmpty())
					for(AABB localBox : shape.toAabbs())
					{
						AABB box = localBox.move(currentPos.getX(),
							currentPos.getY(), currentPos.getZ());
						Optional<Vec3> intercept = box.clip(eyePos, endPos);
						if(intercept.isPresent())
						{
							Vec3 hitVec = intercept.get();
							return new BlockHitResult(hitVec,
								getHitFaceFromBox(hitVec, box), currentPos,
								box.contains(eyePos));
						}
					}
			}

			if(tMaxX < tMaxY)
			{
				if(tMaxX < tMaxZ)
				{
					currentPos = currentPos.offset(stepX, 0, 0);
					tMaxX += tDeltaX;
				}else
				{
					currentPos = currentPos.offset(0, 0, stepZ);
					tMaxZ += tDeltaZ;
				}
			}else if(tMaxY < tMaxZ)
			{
				currentPos = currentPos.offset(0, stepY, 0);
				tMaxY += tDeltaY;
			}else
			{
				currentPos = currentPos.offset(0, 0, stepZ);
				tMaxZ += tDeltaZ;
			}
		}
		return null;
	}

	static boolean isIgnoredBlock(BlockState state)
	{
		Block block = state.getBlock();
		return state.isAir() || block instanceof BushBlock
			|| block instanceof SnowLayerBlock || block instanceof LiquidBlock;
	}

	private static VoxelShape getShape(BlockState state, BlockPos pos)
	{
		FluidState fluidState = WurstClient.MC.level.getFluidState(pos);
		if(fluidState.isSource()
			&& fluidState.getType() == Fluids.WATER)
			return Shapes.block();
		return state.getCollisionShape(WurstClient.MC.level, pos,
			CollisionContext.of(WurstClient.MC.player));
	}

	private static Direction getHitFaceFromBox(Vec3 hit, AABB box)
	{
		if(Math.abs(hit.x - box.minX) <= EPSILON)
			return Direction.WEST;
		if(Math.abs(hit.x - box.maxX) <= EPSILON)
			return Direction.EAST;
		if(Math.abs(hit.y - box.minY) <= EPSILON)
			return Direction.DOWN;
		if(Math.abs(hit.y - box.maxY) <= EPSILON)
			return Direction.UP;
		if(Math.abs(hit.z - box.minZ) <= EPSILON)
			return Direction.NORTH;
		if(Math.abs(hit.z - box.maxZ) <= EPSILON)
			return Direction.SOUTH;

		double min = Math.abs(hit.x - box.minX);
		Direction side = Direction.WEST;
		double east = Math.abs(hit.x - box.maxX);
		if(east < min)
		{
			min = east;
			side = Direction.EAST;
		}
		double down = Math.abs(hit.y - box.minY);
		if(down < min)
		{
			min = down;
			side = Direction.DOWN;
		}
		double up = Math.abs(hit.y - box.maxY);
		if(up < min)
		{
			min = up;
			side = Direction.UP;
		}
		double north = Math.abs(hit.z - box.minZ);
		if(north < min)
		{
			min = north;
			side = Direction.NORTH;
		}
		double south = Math.abs(hit.z - box.maxZ);
		if(south < min)
			side = Direction.SOUTH;
		return side;
	}
}
