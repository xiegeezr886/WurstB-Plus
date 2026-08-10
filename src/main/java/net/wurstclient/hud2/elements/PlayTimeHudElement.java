package net.wurstclient.hud2.elements;

import java.util.Locale;

import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class PlayTimeHudElement extends SoarTextHudElement
{
	private static final long STARTED_AT_NANOS = System.nanoTime();

	public PlayTimeHudElement()
	{
		super("play_time", "\u6e38\u73a9\u65f6\u957f",
			HudElementConfig.HORIZONTAL_RIGHT, 110, 104);
	}

	@Override
	protected String getText()
	{
		long seconds = Math.max(0,
			(System.nanoTime() - STARTED_AT_NANOS) / 1_000_000_000L);
		long hours = seconds / 3600;
		long minutes = seconds % 3600 / 60;
		seconds %= 60;
		return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes,
			seconds);
	}
}
