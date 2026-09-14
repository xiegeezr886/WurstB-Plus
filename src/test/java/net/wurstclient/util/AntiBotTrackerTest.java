package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

final class AntiBotTrackerTest
{
	@Test
	void singleTickObservationsPassImmediatelyWithGraceOfOne()
	{
		AntiBotTracker tracker = new AntiBotTracker(1);
		assertTrue(tracker.noteImpossibleGround(uuid(1), true));
	}

	@Test
	void flagsOnTheNthConsecutiveTick()
	{
		AntiBotTracker tracker = new AntiBotTracker(2);
		UUID id = uuid(1);

		// graceTicks = 2 表示「需要连续成立 2 个 tick」，所以第 1 个 tick
		// 仍在宽限内（返回 false），第 2 个连续 tick 才认定。
		assertFalse(tracker.noteImpossibleGround(id, true));
		assertTrue(tracker.noteImpossibleGround(id, true));
		assertTrue(tracker.noteImpossibleGround(id, true));
	}

	@Test
	void oneCleanTickResetsTheStreak()
	{
		AntiBotTracker tracker = new AntiBotTracker(2);
		UUID id = uuid(1);

		tracker.noteImpossibleGround(id, true);
		assertFalse(tracker.noteImpossibleGround(id, false));
		assertFalse(tracker.noteImpossibleGround(id, true));
		assertTrue(tracker.noteImpossibleGround(id, true));
	}

	@Test
	void tracksEachPlayerIndependently()
	{
		AntiBotTracker tracker = new AntiBotTracker(2);
		UUID a = uuid(1);
		UUID b = uuid(2);

		assertFalse(tracker.noteImpossibleGround(a, true));
		assertFalse(tracker.noteImpossibleGround(b, true));
		assertTrue(tracker.noteImpossibleGround(a, true));
		assertTrue(tracker.noteImpossibleGround(b, true));
	}

	@Test
	void dropsOfflinePlayers()
	{
		AntiBotTracker tracker = new AntiBotTracker(2);
		UUID online = uuid(1);
		UUID offline = uuid(2);

		tracker.noteImpossibleGround(online, true);
		tracker.noteImpossibleGround(offline, true);
		assertEquals(2, tracker.trackedCount());

		tracker.retainOnly(Set.of(online));
		assertEquals(Set.of(online), tracker.trackedUuids());

		tracker.retainOnly(Set.of());
		assertEquals(0, tracker.trackedCount());
	}

	@Test
	void resetClearsEverything()
	{
		AntiBotTracker tracker = new AntiBotTracker(1);
		tracker.noteImpossibleGround(uuid(1), true);
		tracker.reset();
		assertEquals(0, tracker.trackedCount());
	}

	@Test
	void graceIsAtLeastOne()
	{
		AntiBotTracker tracker = new AntiBotTracker(0);
		assertTrue(tracker.noteImpossibleGround(uuid(1), true));
	}

	@Test
	void nullUuidIsIgnored()
	{
		AntiBotTracker tracker = new AntiBotTracker(1);
		assertFalse(tracker.noteImpossibleGround(null, true));
		assertEquals(0, tracker.trackedCount());
	}

	private static UUID uuid(long leastSignificantBits)
	{
		return new UUID(0L, leastSignificantBits);
	}
}
