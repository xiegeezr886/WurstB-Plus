package net.wurstclient.hud2.elements;

import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class NameHudElement extends SoarTextHudElement
{
	public NameHudElement()
	{
		super("player_name", "\u73a9\u5bb6\u540d\u79f0",
			HudElementConfig.HORIZONTAL_RIGHT, 110, 8);
	}

	@Override
	protected String getText()
	{
		LocalPlayer player = WurstClient.MC.player;
		return "Name: " + (player == null ? "Player"
			: player.getGameProfile().getName());
	}
}
