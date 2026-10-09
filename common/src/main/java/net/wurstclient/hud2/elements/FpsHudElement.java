package net.wurstclient.hud2.elements;

import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class FpsHudElement extends TextHudElement
{
	public FpsHudElement()
	{
		super("fps", "FPS");
	}

	@Override
	protected String getText()
	{
		return WurstClient.MC.getFps() + " FPS";
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
			HudElementConfig.VERTICAL_TOP, 3, 55);
	}
}
