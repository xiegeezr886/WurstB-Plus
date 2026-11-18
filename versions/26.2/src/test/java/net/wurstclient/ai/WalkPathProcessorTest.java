package net.wurstclient.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class WalkPathProcessorTest
{
	@Test
	void findsWallAtFeetBeforeHeadHeight()
	{
		BlockPos player = new BlockPos(0, 64, 0);
		BlockPos feetWall = player.east();
		BlockPos headWall = player.above().north();
		assertEquals(feetWall, SpiderPathPlanner.findWall(player,
			pos -> pos.equals(feetWall) || pos.equals(headWall)));
	}

	@Test
	void findsHeadHeightWallAndHandlesOpenSpace()
	{
		BlockPos player = new BlockPos(0, 64, 0);
		BlockPos headWall = player.above().south();
		assertEquals(headWall, SpiderPathPlanner.findWall(player,
			headWall::equals));
		assertNull(SpiderPathPlanner.findWall(player, pos -> false));
	}
}
