package net.wurstclient.hud2.elements;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class CompassHudElement extends SoarTextHudElement
{
	private static final String[] DIRECTIONS = {"S", "SW", "W", "NW", "N",
		"NE", "E", "SE"};

	public CompassHudElement()
	{
		super("compass", "\u6307\u5357\u9488",
			HudElementConfig.HORIZONTAL_LEFT, 110, 56);
	}

	@Override
	protected String getText()
	{
		LocalPlayer player = WurstClient.MC.player;
		if(player == null)
			return "Direction: S";
		float yaw = Mth.wrapDegrees(player.getYHeadRot());
		int index = Mth.floor((yaw + 22.5F) / 45F) & 7;
		return "Direction: " + DIRECTIONS[index];
	}
}
