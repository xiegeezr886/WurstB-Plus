package net.wurstclient.hud2.elements;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class ClockHudElement extends TextHudElement
{
	private static final DateTimeFormatter FORMAT =
		DateTimeFormatter.ofPattern("HH:mm:ss");

	public ClockHudElement()
	{
		super("clock", "Clock");
		setDefaultPosition(3, 169);
	}

	@Override
	public String getText()
	{
		return LocalTime.now().format(FORMAT);
	}
}
