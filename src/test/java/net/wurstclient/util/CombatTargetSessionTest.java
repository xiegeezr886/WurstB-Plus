package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CombatTargetSessionTest
{
	@Test
	void reportsTargetIdentityChangesAndGeneration()
	{
		CombatTargetSession<String> session = new CombatTargetSession<>();
		CombatTargetSession.Selection<String> first = session.update("first",
			value -> true, value -> value.equals("first") ? 0 : 1,
			false, 0, 0);

		assertTrue(first.changed());
		assertEquals(1, first.generation());
		CombatTargetSession.Selection<String> same = session.update("first",
			value -> true, value -> 0, false, 0, 0);
		assertFalse(same.changed());
		assertEquals(1, same.generation());

		CombatTargetSession.Selection<String> cleared = session.clear();
		assertTrue(cleared.changed());
		assertNull(cleared.current());
		assertEquals(2, cleared.generation());
	}

	@Test
	void directTrackingResetsPolicyState()
	{
		CombatTargetSession<String> session = new CombatTargetSession<>();
		session.update("first", value -> true, value -> 0, false, 10, 0);
		session.track("direct");
		session.tick();

		CombatTargetSession.Selection<String> selected = session.update("next",
			value -> true, value -> value.equals("next") ? 0 : 1,
			false, 0, 0);
		assertEquals("next", selected.current());
		assertTrue(selected.changed());
	}
}
