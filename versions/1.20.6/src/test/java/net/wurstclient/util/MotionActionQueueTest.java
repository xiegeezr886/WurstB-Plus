package net.wurstclient.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MotionActionQueueTest
{
	@Test void planSurvivesTheEntityTickIncrementButIsConsumedOnlyOnce()
	{
		var queue = new MotionActionQueue<String>();
		var player = new Object();
		var world = new Object();
		queue.begin(player, world, 10);
		queue.schedule("attack");
		assertEquals("attack", queue.consume(player, world, 11));
		assertNull(queue.consume(player, world, 11));
	}

	@Test void staleWorldPlayerAndTickNeverExecute()
	{
		var queue = new MotionActionQueue<String>();
		var player = new Object();
		var world = new Object();
		for(int scenario = 0; scenario < 4; scenario++)
		{
			queue.begin(player, world, 10);
			queue.schedule("attack");
			assertNull(queue.consume(scenario == 0 ? new Object() : player,
				scenario == 1 ? new Object() : world, scenario == 2 ? 12 : scenario == 3 ? 9 : 10));
		}
	}
}
