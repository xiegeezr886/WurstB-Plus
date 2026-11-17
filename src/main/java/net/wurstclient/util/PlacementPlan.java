package net.wurstclient.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public record PlacementPlan(BlockPos target, BlockPos neighbor,
	Direction side, Vec3 hitVec, Rotation rotation, double score)
{
}
