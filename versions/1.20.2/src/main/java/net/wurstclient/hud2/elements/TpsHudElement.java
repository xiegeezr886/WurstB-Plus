package net.wurstclient.hud2.elements;

import java.util.Locale;

import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class TpsHudElement extends TextHudElement
{
	public TpsHudElement()
	{
		super("tps", "TPS");
	}

	@Override
	protected String getText()
	{
		return String.format(Locale.ROOT, "%.1f TPS",
			WurstClient.INSTANCE.getClientMetricsManager().getTicksPerSecond());
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
			HudElementConfig.VERTICAL_TOP, 3, 97);
	}
}
