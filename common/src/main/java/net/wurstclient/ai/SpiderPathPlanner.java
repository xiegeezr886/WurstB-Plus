package net.wurstclient.ai;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

final class SpiderPathPlanner
{
	private SpiderPathPlanner()
	{
	}

	static BlockPos findWall(BlockPos pos, Predicate<BlockPos> isSolid)
	{
		for(Direction direction : Direction.Plane.HORIZONTAL)
		{
			BlockPos wall = pos.relative(direction);
			if(isSolid.test(wall))
				return wall;
		}
		for(Direction direction : Direction.Plane.HORIZONTAL)
		{
			BlockPos wall = pos.above().relative(direction);
			if(isSolid.test(wall))
				return wall;
		}
		return null;
	}
}
