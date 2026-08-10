package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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

	@Test
	void ignoresInvalidCandidateWhileCurrentTargetIsValid()
	{
		TargetTracker<String> tracker = new TargetTracker<>();
		tracker.update("current", "current"::equals, value -> 1, false, 0, 0);

		assertEquals("current", tracker.update("invalid", "current"::equals,
			value -> {
				throw new AssertionError("invalid candidate must not be scored");
			}, false, 0, 0));
	}

	@Test
	void replacesTargetWithInvalidScoreButRejectsInvalidCandidateScore()
	{
		TargetTracker<String> tracker = new TargetTracker<>();
		tracker.update("current", value -> true, value -> 0, false, 0, 0);

		assertEquals("candidate", tracker.update("candidate", value -> true,
			value -> value.equals("current") ? Double.NaN : 1, false, 0, 0));
		assertEquals("candidate", tracker.update("other", value -> true,
			value -> value.equals("other") ? Double.POSITIVE_INFINITY : 1,
			false, 0, 0));
	}

	@Test
	void validatesEachDistinctTargetAtMostOncePerUpdate()
	{
		TargetTracker<String> tracker = new TargetTracker<>();
		tracker.update("current", value -> true, value -> 10, false, 0, 0);
		AtomicInteger calls = new AtomicInteger();

		tracker.update("candidate", value -> {
			calls.incrementAndGet();
			return true;
		}, value -> value.equals("current") ? 10 : 5, false, 0, 0);
		assertEquals(2, calls.get());
	}

	@Test
	void sanitizesNonFiniteSwitchAdvantage()
	{
		TargetTracker<String> tracker = new TargetTracker<>();
		tracker.update("current", value -> true, value -> 10, false, 0, 0);

		assertEquals("candidate", tracker.update("candidate", value -> true,
			value -> value.equals("current") ? 10 : 9.9, false, 0,
			Double.NaN));
	}
}
