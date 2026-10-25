package net.wurstclient.hud2.elements;

import net.minecraft.client.multiplayer.ServerData;
import net.wurstclient.WurstClient;

public final class ServerHudElement extends TextHudElement
{
	public ServerHudElement()
	{
		super("server", "Server");
		setDefaultPosition(3, 150);
	}

	@Override
	public String getText()
	{
		if(WurstClient.MC.hasSingleplayerServer())
			return "Singleplayer";
		ServerData server = WurstClient.MC.getCurrentServer();
		return server == null ? "No server" : server.ip;
	}
}
