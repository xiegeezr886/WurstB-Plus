package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class RollingClickArrayTest
{
	@Test
	void pushesIntoOppositeCycle()
	{
		RollingClickArray array = new RollingClickArray(3, 2);
		array.push(new int[]{1, 2, 3});
		assertArrayEquals(new int[]{0, 0, 0, 1, 2, 3}, array.copyArray());

		assertTrue(array.advance(3));
		array.push(new int[]{4, 5, 6});
		assertArrayEquals(new int[]{4, 5, 6, 1, 2, 3}, array.copyArray());
	}

	@Test
	void readsRelativeToCircularHead()
	{
		RollingClickArray array = new RollingClickArray(2, 2);
		array.set(0, 7);
		assertFalse(array.advance());
		assertEquals(7, array.get(-1));
	}
}
