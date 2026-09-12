package net.wurstclient.music.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class SpringTest
{
	@Test
	void startsSettledAtTheInitialPosition()
	{
		Spring spring = new Spring(12);
		assertEquals(12, spring.getCurrentPosition(), 1e-9);
		assertTrue(spring.arrived());
	}

	@Test
	void setPositionSnapsWithoutOscillating()
	{
		Spring spring = new Spring(0);
		spring.setPosition(40);
		assertEquals(40, spring.getCurrentPosition(), 1e-9);
		assertTrue(spring.arrived());
		spring.update(0.016);
		assertEquals(40, spring.getCurrentPosition(), 1e-9);
	}

	@Test
	void overdampedTargetConvergesWithoutOvershoot()
	{
		Spring spring = new Spring(0);
		spring.updateParams(80, 40, 1, true);
		spring.setTargetPosition(10, 0);
		double previous = spring.getCurrentPosition();
		for(int i = 0; i < 240; i++)
		{
			spring.update(1 / 60D);
			double current = spring.getCurrentPosition();
			assertTrue(current + 1e-9 >= previous,
				"soft spring should not reverse toward the start");
			assertTrue(current <= 10 + 1e-6,
				"soft spring should not overshoot the target");
			previous = current;
			if(spring.arrived())
				break;
		}
		assertTrue(spring.arrived());
		assertEquals(10, spring.getCurrentPosition(), 0.02);
	}

	@Test
	void delayedTargetDoesNotMoveUntilDelayElapses()
	{
		Spring spring = new Spring(0);
		spring.setTargetPosition(8, 0.05);
		spring.update(0.016);
		assertEquals(0, spring.getCurrentPosition(), 1e-9);
		assertFalse(spring.arrived());
		for(int i = 0; i < 180; i++)
		{
			spring.update(1 / 60D);
			if(spring.arrived())
				break;
		}
		assertTrue(spring.arrived());
		assertEquals(8, spring.getCurrentPosition(), 0.02);
	}
}
