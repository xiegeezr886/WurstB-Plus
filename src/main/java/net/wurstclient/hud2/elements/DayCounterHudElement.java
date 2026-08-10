package net.wurstclient.hud2.elements;

import net.minecraft.client.multiplayer.ClientLevel;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class DayCounterHudElement extends SoarTextHudElement
{
	public DayCounterHudElement()
	{
		super("day_counter", "\u4e16\u754c\u5929\u6570",
			HudElementConfig.HORIZONTAL_LEFT, 110, 104);
	}

	@Override
	protected String getText()
	{
		ClientLevel level = WurstClient.MC.level;
		long day = level == null ? 0 : Math.floorDiv(level.getDayTime(), 24000L);
		return day + " Day" + (day == 1 ? "" : "s");
	}
}
