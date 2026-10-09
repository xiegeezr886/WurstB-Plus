package net.wurstclient.hud2.elements;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class ClockHudElement extends TextHudElement
{
	private static final DateTimeFormatter FORMAT =
		DateTimeFormatter.ofPattern("HH:mm:ss");

	public ClockHudElement()
	{
		super("clock", "Clock");
	}

	@Override
	protected String getText()
	{
		return LocalTime.now().format(FORMAT);
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
			HudElementConfig.VERTICAL_TOP, 3, 142);
	}
}
