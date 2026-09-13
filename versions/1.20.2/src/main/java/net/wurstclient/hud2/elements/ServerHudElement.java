package net.wurstclient.hud2.elements;

import net.minecraft.client.multiplayer.ServerData;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class ServerHudElement extends TextHudElement
{
	public ServerHudElement()
	{
		super("server", "Server");
	}

	@Override
	protected String getText()
	{
		if(WurstClient.MC.hasSingleplayerServer())
			return "Singleplayer";
		ServerData server = WurstClient.MC.getCurrentServer();
		return server == null ? "No server" : server.ip;
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
			HudElementConfig.VERTICAL_TOP, 3, 127);
	}
}
