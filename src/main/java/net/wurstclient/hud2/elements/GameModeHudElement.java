package net.wurstclient.hud2.elements;

import net.minecraft.world.level.GameType;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class GameModeHudElement extends SoarTextHudElement
{
	public GameModeHudElement()
	{
		super("game_mode", "\u6e38\u620f\u6a21\u5f0f",
			HudElementConfig.HORIZONTAL_LEFT, 110, 152);
	}

	@Override
	protected String getText()
	{
		if(WurstClient.MC.gameMode == null)
			return "Mode: Survival";
		GameType mode = WurstClient.MC.gameMode.getPlayerMode();
		String name = switch(mode)
		{
			case CREATIVE -> "Creative";
			case ADVENTURE -> "Adventure";
			case SPECTATOR -> "Spectator";
			default -> "Survival";
		};
		return "Mode: " + name;
	}
}
