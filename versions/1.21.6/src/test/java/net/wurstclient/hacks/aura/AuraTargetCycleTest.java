package net.wurstclient.hacks.aura;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class AuraTargetCycleTest
{
	@Test void reorderingDoesNotChangeTheHeldTarget()
	{
		var cycle = new AuraTargetCycle<String>();
		assertEquals("a", cycle.select(List.of("a", "b"), true, 300, 0));
		assertEquals("a", cycle.select(List.of("b", "a"), true, 300, 299));
		assertEquals("b", cycle.select(List.of("b", "a"), true, 300, 300));
	}

	@Test void everyTargetGetsATurnEvenWhenPriorityChanges()
	{
		var cycle = new AuraTargetCycle<String>();
		assertEquals("a", cycle.select(List.of("a", "b", "c"), true, 100, 0));
		assertEquals("b", cycle.select(List.of("a", "b", "c"), true, 100, 100));
		assertEquals("c", cycle.select(List.of("b", "a", "c"), true, 100, 200));
		assertEquals("a", cycle.select(List.of("c", "a", "b"), true, 100, 300));
	}

	@Test void removalImmediatelySelectsALiveReplacement()
	{
		var cycle = new AuraTargetCycle<String>();
		cycle.select(List.of("a", "b"), true, 1000, 0);
		assertEquals("b", cycle.select(List.of("b"), true, 1000, 1));
		assertNull(cycle.select(List.of(), true, 1000, 2));
		assertEquals("a", cycle.select(List.of("a", "b"), true, 1000, 3));
	}

	@Test void singleTracksPriorityAndSwitchStartsWithFreshHold()
	{
		var cycle = new AuraTargetCycle<String>();
		assertEquals("a", cycle.select(List.of("a", "b"), false, 100, 0));
		assertEquals("b", cycle.select(List.of("b", "a"), false, 100, 50));
		assertEquals("b", cycle.select(List.of("a", "b"), true, 100, 51));
		assertEquals("a", cycle.select(List.of("a", "b"), true, 100, 150));
	}

	@Test void resetDropsPreviousWorldIdentityAndSwitchHistory()
	{
		var cycle = new AuraTargetCycle<String>();
		cycle.select(List.of("a", "b"), true, 100, 0);
		cycle.reset();
		assertEquals("b", cycle.select(List.of("b", "a"), true, 100, 1));
	}

	@Test void zeroDelayAndSingleCandidateRemainValid()
	{
		var cycle = new AuraTargetCycle<String>();
		assertEquals("a", cycle.select(List.of("a"), true, 0, 0));
		assertEquals("a", cycle.select(List.of("a"), true, 0, 10));
		assertEquals("b", cycle.select(List.of("a", "b"), true, 0, 20));
	}
}
