package net.wurstclient.util.anim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

final class EasedTweenTest
{
	private static final float EPSILON = 0.0001F;

	/** 可控时钟，让补间行为完全确定（System::nanoTime 测不了）。 */
	private static final class FakeClock implements java.util.function.LongSupplier
	{
		private final AtomicLong nanos = new AtomicLong();

		@Override
		public long getAsLong()
		{
			return nanos.get();
		}

		void advanceMillis(long millis)
		{
			nanos.addAndGet(millis * 1_000_000L);
		}
	}

	@Test
	void aTweenStartsAtItsInitialValue()
	{
		EasedTween tween = new EasedTween(7F, 100, Easings.LINEAR, new FakeClock());

		assertEquals(7F, tween.get(), EPSILON);
		assertTrue(tween.isSettled());
	}

	@Test
	void updateIsIdempotentWhileTheTargetIsUnchanged()
	{
		FakeClock clock = new FakeClock();
		EasedTween tween = new EasedTween(0F, 100, Easings.LINEAR, clock);

		float first = tween.update(1F);
		float second = tween.update(1F);
		float third = tween.update(1F);

		assertEquals(first, second, EPSILON);
		assertEquals(second, third, EPSILON);
	}

	@Test
	void linearInterpolatesHalfwayAtHalfTheDuration()
	{
		FakeClock clock = new FakeClock();
		EasedTween tween = new EasedTween(0F, 100, Easings.LINEAR, clock);

		tween.update(10F);
		clock.advanceMillis(50);

		assertEquals(5F, tween.update(10F), EPSILON);
	}

	@Test
	void theTweenLandsExactlyOnTheTargetWhenTimeIsUp()
	{
		FakeClock clock = new FakeClock();
		EasedTween tween = new EasedTween(0F, 100, Easings.LINEAR, clock);

		tween.update(10F);
		clock.advanceMillis(100);

		assertEquals(10F, tween.update(10F), EPSILON);
		assertTrue(tween.isSettled());
	}

	@Test
	void aZeroDurationTweenSettlesInsteadOfDividingByZero()
	{
		FakeClock clock = new FakeClock();
		EasedTween tween = new EasedTween(0F, 0, Easings.LINEAR, clock);

		// 参考的 getProgress() 在这里会得到 Infinity
		assertEquals(3F, tween.update(3F), EPSILON);
		assertTrue(tween.isSettled());

		// 负时长同样被夹到 1ms
		EasedTween negative =
			new EasedTween(0F, -50, Easings.LINEAR, new FakeClock());
		assertEquals(3F, negative.update(3F), EPSILON);
	}

	@Test
	void aCurveThatDoesNotReachOneStillLandsOnTheTarget()
	{
		FakeClock clock = new FakeClock();
		// 照搬参考的 DYNAMIC_ISLAND：f(1) ≈ 1.004，不收口就永远差 0.4%
		EasedTween tween =
			new EasedTween(0F, 100, Easings.DYNAMIC_ISLAND, clock);

		tween.update(1F);
		clock.advanceMillis(100);

		assertEquals(1F, tween.update(1F), EPSILON);
		assertTrue(tween.isSettled());
	}

	@Test
	void progressBeyondTheDurationIsClamped()
	{
		FakeClock clock = new FakeClock();
		EasedTween tween = new EasedTween(0F, 100, Easings.LINEAR, clock);

		tween.update(1F);
		// 远超时长：参考的 getProgress() 会给出 > 1
		clock.advanceMillis(10_000);

		assertEquals(1F, tween.update(1F), EPSILON);
	}

	@Test
	void overshootingCurvesMayLeaveTheRangeMidFlightButNotAtTheEnd()
	{
		FakeClock clock = new FakeClock();
		EasedTween tween = new EasedTween(1F, 100, Easings.EASE_IN_BACK, clock);

		tween.update(0F);
		clock.advanceMillis(50);

		// EASE_IN_BACK 在 x=0.5 处的进度是负数（≈ -0.0877）。从 1 走向 0 时，
		// 负进度意味着先往反方向冲过起点，所以值应当 > 1 而不是 < 0。
		float mid = tween.update(0F);
		assertTrue(mid > 1F, "expected EASE_IN_BACK to overshoot, got " + mid);

		clock.advanceMillis(50);
		assertEquals(0F, tween.update(0F), EPSILON);
	}

	@Test
	void retargetingStartsFromTheCurrentValueNotTheOldStart()
	{
		FakeClock clock = new FakeClock();
		EasedTween tween = new EasedTween(0F, 100, Easings.LINEAR, clock);

		tween.update(10F);
		clock.advanceMillis(50);
		assertEquals(5F, tween.update(10F), EPSILON);

		// 中途改目标：应当从 5 继续走到 0，而不是从 0 重新开始
		float justRetargeted = tween.update(0F);
		assertEquals(5F, justRetargeted, EPSILON);

		clock.advanceMillis(50);
		assertEquals(2.5F, tween.update(0F), EPSILON);
	}

	@Test
	void snapJumpsToTheValueAndSettles()
	{
		FakeClock clock = new FakeClock();
		EasedTween tween = new EasedTween(0F, 100, Easings.LINEAR, clock);

		tween.update(10F);
		tween.snap(42F);

		assertEquals(42F, tween.get(), EPSILON);
		assertEquals(42F, tween.getTarget(), EPSILON);
		assertTrue(tween.isSettled());

		clock.advanceMillis(1000);
		assertEquals(42F, tween.update(42F), EPSILON);
	}

	@Test
	void movingToANewTargetUnsettlesTheTween()
	{
		EasedTween tween =
			new EasedTween(0F, 100, Easings.LINEAR, new FakeClock());

		assertTrue(tween.isSettled());
		tween.update(5F);
		assertFalse(tween.isSettled());
	}

	@Test
	void rejectsNullArguments()
	{
		assertThrows(IllegalArgumentException.class,
			() -> new EasedTween(0F, 100, null));
		assertThrows(IllegalArgumentException.class,
			() -> new EasedTween(0F, 100, Easings.LINEAR, null));
	}
}
