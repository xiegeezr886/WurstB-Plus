package net.wurstclient.hud2.elements;

import net.minecraft.client.multiplayer.ClientLevel;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class WeatherHudElement extends SoarTextHudElement
{
	public WeatherHudElement()
	{
		super("weather", "\u5929\u6c14", HudElementConfig.HORIZONTAL_RIGHT,
			110, 248);
	}

	@Override
	protected String getText()
	{
		ClientLevel level = WurstClient.MC.level;
		if(level == null)
			return "Weather: Clear";
		if(level.isThundering())
			return "Weather: Thunder";
		if(level.isRaining())
			return "Weather: Rain";
		return "Weather: Clear";
	}
}
