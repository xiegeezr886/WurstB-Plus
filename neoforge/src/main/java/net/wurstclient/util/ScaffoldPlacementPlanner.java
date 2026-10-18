package net.wurstclient.util;

import java.util.LinkedHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.util.BlockPlacer.BlockPlacingParams;

public enum ScaffoldPlacementPlanner
{
	;

	private static final double MAX_REACH_SQ = 4.55 * 4.55;

	public static PlacementPlan find(Vec3 predictedPosition,
		PlacementPlan previous, boolean requireLineOfSight)
	{
		BlockPos desired = BlockPos.containing(predictedPosition).below();
		LinkedHashSet<BlockPos> candidates = new LinkedHashSet<>();
		candidates.add(desired);

		for(int radius = 1; radius <= 2; radius++)
			for(int x = -radius; x <= radius; x++)
				for(int z = -radius; z <= radius; z++)
				{
					if(Math.abs(x) + Math.abs(z) != radius)
						continue;
					candidates.add(desired.offset(x, 0, z));
				}

		PlacementPlan best = null;
		for(BlockPos target : candidates)
		{
			PlacementPlan plan = evaluate(target, desired, previous,
				requireLineOfSight);
			if(plan != null && (best == null || plan.score() < best.score()))
				best = plan;
		}
		return best;
	}

	public static PlacementPlan findAt(BlockPos target, PlacementPlan previous,
		boolean requireLineOfSight)
	{
		return evaluate(target, target, previous, requireLineOfSight);
	}

	private static PlacementPlan evaluate(BlockPos target, BlockPos desired,
		PlacementPlan previous, boolean requireLineOfSight)
	{
		if(!BlockUtils.getState(target).canBeReplaced())
			return null;

		BlockPlacingParams params = BlockPlacer.getBlockPlacingParams(target);
		if(params == null || params.distanceSq() > MAX_REACH_SQ
			|| requireLineOfSight && !params.lineOfSight())
			return null;

		double targetDistance = target.distSqr(desired);
		double facePenalty = switch(params.side())
		{
			case UP -> 0;
			case NORTH, SOUTH, EAST, WEST -> 1.5;
			case DOWN -> 5;
		};
		double continuityPenalty = previous != null
			&& (!previous.neighbor().equals(params.neighbor())
				|| previous.side() != params.side()) ? 0.75 : 0;
		double visibilityPenalty = params.lineOfSight() ? 0 : 8;
		double score = targetDistance * 6 + params.distanceSq() * 0.15
			+ facePenalty + continuityPenalty + visibilityPenalty;

		return new PlacementPlan(target, params.neighbor(), params.side(),
			params.hitVec(), RotationUtils.getNeededRotations(params.hitVec()),
			score);
	}
}
