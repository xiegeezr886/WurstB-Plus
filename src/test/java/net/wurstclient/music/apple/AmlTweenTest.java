package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 校验 {@link AmlTween} 与 CSS {@code transition} 的等价性。
 */
final class AmlTweenTest
{
	private static final double EPS = 1e-6;

	@Test
	void reachesTargetExactlyAtDurationEnd()
	{
		AmlTween tween = new AmlTween(0, true);
		tween.setTarget(1, 0.4);
		assertEquals(0, tween.get(), EPS);
		tween.update(0.2);
		assertTrue(tween.get() > 0 && tween.get() < 1,
			"中途应处于起止之间，实际 " + tween.get());
		tween.update(0.2);
		assertEquals(1, tween.get(), EPS);
		assertFalse(tween.isAnimating());
	}

	@Test
	void easeOutRunsAheadOfLinear()
	{
		AmlTween tween = new AmlTween(0, true);
		tween.setTarget(1, 1);
		tween.update(0.25);
		assertTrue(tween.get() > 0.25, "ease-out 应快于线性");
	}

	@Test
	void retargetRestartsFromCurrentValue()
	{
		AmlTween tween = new AmlTween(0, true);
		tween.setTarget(1, 1);
		tween.update(0.5);
		double midway = tween.get();
		tween.setTarget(0, 1);
		// 从当前值往回走，不应瞬移
		assertEquals(midway, tween.get(), EPS);
		tween.update(1);
		assertEquals(0, tween.get(), EPS);
	}

	@Test
	void zeroDurationAppliesImmediately()
	{
		AmlTween tween = new AmlTween(0.2, true);
		tween.setTarget(1, 0);
		assertEquals(1, tween.get(), EPS);
	}

	@Test
	void snapToJumpsWithoutAnimating()
	{
		AmlTween tween = new AmlTween(0.2, true);
		tween.setTarget(1, 0.4);
		tween.snapTo(0.85);
		assertEquals(0.85, tween.get(), EPS);
		assertFalse(tween.isAnimating());
	}

	@Test
	void updateWithoutTargetChangeKeepsValue()
	{
		AmlTween tween = new AmlTween(0.5, true);
		tween.update(1);
		assertEquals(0.5, tween.get(), EPS);
	}
}
