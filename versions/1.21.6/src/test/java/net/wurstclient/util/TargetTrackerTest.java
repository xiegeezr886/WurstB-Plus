package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class TargetTrackerTest
{
	@Test
	void keepsStickyTargetWhileValid()
	{
		TargetTracker<String> tracker = new TargetTracker<>();
		Map<String, Double> scores = Map.of("first", 4.0, "better", 1.0);
		tracker.update("first", value -> true, scores::get, false, 0, 0);

		assertEquals("first", tracker.update("better", value -> true,
			scores::get, true, 0, 0));
	}

	@Test
	void respectsCooldownAndRequiredAdvantage()
	{
		TargetTracker<String> tracker = new TargetTracker<>();
		Map<String, Double> scores = Map.of("first", 10.0, "small", 9.5,
			"better", 5.0);
		tracker.update("first", value -> true, scores::get, false, 2, 10);

		assertEquals("first", tracker.update("better", value -> true,
			scores::get, false, 2, 10));
		tracker.tick();
		tracker.tick();
		assertEquals("first", tracker.update("small", value -> true,
			scores::get, false, 2, 10));
		assertEquals("better", tracker.update("better", value -> true,
			scores::get, false, 2, 10));
	}

	@Test
	void rejectsInvalidReplacementCandidate()
	{
		TargetTracker<String> tracker = new TargetTracker<>();

		assertNull(tracker.update("invalid", "valid"::equals,
			value -> 0, false, 2, 0));
	}
}
