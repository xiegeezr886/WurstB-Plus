package net.wurstclient.hud2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class HudNotificationRendererTest
{
	@Test
	void lifetimeProgressFillsAcrossDisplayDuration()
	{
		long spawn = 5_000_000_000L;
		assertEquals(0, HudNotification.lifetimeProgress(spawn, spawn,
			HudNotification.DEFAULT_DURATION));
		assertEquals(0.5F, HudNotification.lifetimeProgress(spawn,
			spawn + HudNotification.DEFAULT_DURATION / 2,
			HudNotification.DEFAULT_DURATION), 0.0001F);
		assertEquals(1, HudNotification.lifetimeProgress(spawn,
			spawn + HudNotification.DEFAULT_DURATION,
			HudNotification.DEFAULT_DURATION));
	}

	@Test
	void lifetimeProgressStaysWithinBarBounds()
	{
		long spawn = 5_000_000_000L;
		assertEquals(0, HudNotification.lifetimeProgress(spawn, spawn - 1,
			HudNotification.DEFAULT_DURATION));
		assertEquals(1, HudNotification.lifetimeProgress(spawn,
			spawn + HudNotification.DEFAULT_DURATION * 2,
			HudNotification.DEFAULT_DURATION));
	}

	@Test
	void persistentNotificationsNeverExpire()
	{
		long spawn = 5_000_000_000L;
		assertEquals(0, HudNotification.lifetimeProgress(spawn,
			spawn + 60_000L, HudNotification.PERSISTENT));
	}
}
