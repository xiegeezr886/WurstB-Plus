package net.wurstclient.hud2.elements;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class PlayerCounterHudElement extends SoarTextHudElement
{
	public PlayerCounterHudElement()
	{
		super("player_counter", "\u5728\u7ebf\u4eba\u6570",
			HudElementConfig.HORIZONTAL_RIGHT, 110, 56);
	}

	@Override
	protected String getText()
	{
		ClientPacketListener connection = WurstClient.MC.getConnection();
		int count = connection == null ? 0 : connection.getOnlinePlayers().size();
		return "Players: " + count;
	}
}
