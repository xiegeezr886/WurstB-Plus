package net.wurstclient.hud2.elements;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class PingHudElement extends TextHudElement
{
	public PingHudElement()
	{
		super("ping", "Ping");
	}

	@Override
	protected String getText()
	{
		if(WurstClient.MC.player == null)
			return "0ms";
		PlayerInfo info = WurstClient.MC.player.connection
			.getPlayerInfo(WurstClient.MC.player.getUUID());
		return (info == null ? 0 : info.getLatency()) + "ms";
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
			HudElementConfig.VERTICAL_TOP, 3, 82);
	}
}
