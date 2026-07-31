package net.wurstclient.hud2.elements;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class CoordsHudElement extends TextHudElement
{
	public CoordsHudElement()
	{
		super("coords", "Coordinates");
	}

	@Override
	protected String getText()
	{
		LocalPlayer player = WurstClient.MC.player;
		if(player == null)
			return "XYZ: - - -";
		int x = (int)Math.floor(player.getX());
		int y = (int)Math.floor(player.getY());
		int z = (int)Math.floor(player.getZ());
		String text = "XYZ: " + x + " " + y + " " + z;
		if(player.level().dimension() == Level.NETHER)
			return text + " OW: " + x * 8 + " " + z * 8;
		if(player.level().dimension() == Level.OVERWORLD)
			return text + " Nether: " + Math.floorDiv(x, 8) + " "
				+ Math.floorDiv(z, 8);
		return text;
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
			HudElementConfig.VERTICAL_TOP, 3, 70);
	}
}
