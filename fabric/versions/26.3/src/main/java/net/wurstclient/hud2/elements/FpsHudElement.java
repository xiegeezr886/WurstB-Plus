package net.wurstclient.hud2.elements;

import net.wurstclient.WurstClient;

public final class FpsHudElement extends TextHudElement
{
	public FpsHudElement()
	{
		super("fps", "FPS");
		setDefaultPosition(3, 55);
	}

	@Override
	public String getText()
	{
		return WurstClient.MC.getFps() + " FPS";
	}
}
