package net.wurstclient.clickgui2.component;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ClickGuiKeyLatchTest
{
	@Test
	void openingPressIsIgnoredUntilBindingIsReleased()
	{
		ClickGuiKeyLatch latch = new ClickGuiKeyLatch();
		assertFalse(latch.shouldCloseOnPress(true));
		assertTrue(latch.armOnRelease(true));
		assertTrue(latch.shouldCloseOnPress(true));
	}

	@Test
	void unrelatedKeysDoNotArmClosing()
	{
		ClickGuiKeyLatch latch = new ClickGuiKeyLatch();
		assertFalse(latch.armOnRelease(false));
		assertFalse(latch.shouldCloseOnPress(true));
	}
}
