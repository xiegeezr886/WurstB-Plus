package net.wurstclient.hud2.elements;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.wurstclient.WurstClient;

public final class PingHudElement extends TextHudElement
{
	public PingHudElement()
	{
		super("ping", "Ping");
		setDefaultPosition(3, 93);
	}

	@Override
	public String getText()
	{
		if(WurstClient.MC.player == null)
			return "0ms";
		PlayerInfo info = WurstClient.MC.player.connection
			.getPlayerInfo(WurstClient.MC.player.getUUID());
		return (info == null ? 0 : info.getLatency()) + "ms";
	}
}
