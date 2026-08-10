package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class CombatIntentQueueTest
{
	@Test
	void attackOverridesMissAndIsConsumedOnce()
	{
		CombatIntentQueue<String> queue = new CombatIntentQueue<>();
		queue.scheduleMiss();
		queue.scheduleAttack("target");

		CombatIntentQueue.Intent<String> intent = queue.consume();
		assertEquals(CombatIntentQueue.Kind.ATTACK, intent.kind());
		assertEquals("target", intent.target());
		assertEquals(CombatIntentQueue.Kind.NONE, queue.consume().kind());
	}

	@Test
	void tickBoundaryClearsStaleIntent()
	{
		CombatIntentQueue<String> queue = new CombatIntentQueue<>();
		queue.scheduleAttack("target");
		queue.beginTick();

		CombatIntentQueue.Intent<String> intent = queue.consume();
		assertEquals(CombatIntentQueue.Kind.NONE, intent.kind());
		assertNull(intent.target());
		assertThrows(NullPointerException.class,
			() -> queue.scheduleAttack(null));
	}
}
