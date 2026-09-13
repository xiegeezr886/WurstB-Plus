package net.wurstclient.hud2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class HudNotificationRendererTest
{
	@Test
	void lifetimeProgressFillsAcrossDisplayDuration()
	{
		long spawn = 5_000_000_000L;
		assertEquals(0,
			HudNotificationRenderer.lifetimeProgress(spawn, spawn));
		assertEquals(0.5F, HudNotificationRenderer.lifetimeProgress(spawn,
			spawn + HudNotificationRenderer.DURATION_NANOS / 2), 0.0001F);
		assertEquals(1, HudNotificationRenderer.lifetimeProgress(spawn,
			spawn + HudNotificationRenderer.DURATION_NANOS));
	}

	@Test
	void lifetimeProgressStaysWithinBarBounds()
	{
		long spawn = 5_000_000_000L;
		assertEquals(0,
			HudNotificationRenderer.lifetimeProgress(spawn, spawn - 1));
		assertEquals(1, HudNotificationRenderer.lifetimeProgress(spawn,
			spawn + HudNotificationRenderer.DURATION_NANOS * 2));
	}
}
