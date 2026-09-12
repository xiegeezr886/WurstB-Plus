package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class InterludeDotsTest
{
	@Test
	void staysHiddenDuringTheFirst500Ms()
	{
		InterludeDots dots = new InterludeDots();
		dots.setInterlude(0, 8_000, 0, true);
		dots.update(200);
		assertFalse(dots.isVisible());
		assertEquals(0, dots.dotOpacity(0), 1e-4);
	}

	@Test
	void lightsDotsInSequenceAfterFadeIn()
	{
		InterludeDots dots = new InterludeDots();
		dots.setInterlude(0, 8_000, 0, true);
		dots.update(3_000);
		assertTrue(dots.isVisible());
		assertTrue(dots.dotOpacity(0) > dots.dotOpacity(2));
		assertTrue(dots.scale() > 0.3F);
	}

	@Test
	void contractsNearTheEnd()
	{
		InterludeDots dots = new InterludeDots();
		dots.setInterlude(0, 8_000, 0, true);
		dots.update(3_000);
		float mid = dots.scale();
		dots.update(7_980);
		assertTrue(dots.scale() < mid);
	}

	@Test
	void easingHelpersMatchAmlSource()
	{
		assertEquals(0, InterludeDots.easeOutExpo(0), 1e-9);
		assertEquals(1, InterludeDots.easeOutExpo(1), 1e-9);
		assertEquals(0, InterludeDots.easeInOutBack(0), 1e-6);
		assertEquals(1, InterludeDots.easeInOutBack(1), 1e-6);
	}
}
