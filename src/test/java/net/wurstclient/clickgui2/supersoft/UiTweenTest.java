package net.wurstclient.clickgui2.supersoft;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class UiTweenTest
{
	@Test
	void startsAtConfiguredValueAndCanSnap()
	{
		UiTween tween = new UiTween(0.25F, 150);
		assertEquals(0.25F, tween.get());
		tween.snap(0.75F);
		assertEquals(0.75F, tween.update(0.75F));
	}

	@Test
	void beginsMovingWithoutJumpingToTarget()
	{
		UiTween tween = new UiTween(0, 150);
		float first = tween.update(1);
		assertTrue(first >= 0 && first < 1);
	}
}
