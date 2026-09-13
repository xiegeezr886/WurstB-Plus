package net.wurstclient.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AnimFloatTest
{
	@Test
	void snapJumpsWithoutAnimating()
	{
		AnimFloat value = new AnimFloat(0.25F, 8);
		value.snap(0.8F);
		assertEquals(0.8F, value.get());
		assertFalse(value.update(1));
		assertEquals(0.8F, value.get());
	}

	@Test
	void exponentialSmoothingApproachesTheTarget()
	{
		AnimFloat value = new AnimFloat(0, 12);
		value.set(1);
		float previous = value.get();
		boolean stillMoving = true;
		for(int i = 0; i < 120 && stillMoving; i++)
		{
			stillMoving = value.update(1 / 60F);
			float current = value.get();
			assertTrue(current + 1e-6F >= previous);
			assertTrue(current <= 1 + 1e-6F);
			previous = current;
		}
		assertFalse(stillMoving);
		assertEquals(1, value.get());
	}

	@Test
	void tinyDeltaSnapsWhenAlreadyClose()
	{
		AnimFloat value = new AnimFloat(1, 4);
		value.set(1.001F);
		assertFalse(value.update(0));
		assertEquals(1.001F, value.get());
	}
}
