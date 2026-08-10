package net.wurstclient.hud2.elements;

import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class HealthHudElement extends SoarTextHudElement
{
	public HealthHudElement()
	{
		super("health", "\u751f\u547d\u503c", HudElementConfig.HORIZONTAL_LEFT,
			110, 200);
	}

	@Override
	protected String getText()
	{
		LocalPlayer player = WurstClient.MC.player;
		return (player == null ? 20 : (int)player.getHealth()) + " Health";
	}
}
