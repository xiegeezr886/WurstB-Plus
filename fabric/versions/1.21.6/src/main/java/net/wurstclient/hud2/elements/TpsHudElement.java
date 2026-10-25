package net.wurstclient.hud2.elements;

import java.util.Locale;

import net.wurstclient.WurstClient;

public final class TpsHudElement extends TextHudElement
{
	public TpsHudElement()
	{
		super("tps", "TPS");
		setDefaultPosition(3, 112);
	}

	@Override
	public String getText()
	{
		return String.format(Locale.ROOT, "%.1f TPS",
			WurstClient.INSTANCE.getClientMetricsManager().getTicksPerSecond());
	}
}
