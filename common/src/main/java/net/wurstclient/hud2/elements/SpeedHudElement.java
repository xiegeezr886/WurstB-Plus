package net.wurstclient.hud2.elements;

import java.util.Locale;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.HudLayout.HudElementConfig;

public final class SpeedHudElement extends TextHudElement
{
	public SpeedHudElement()
	{
		super("speed", "Speed");
	}

	@Override
	protected String getText()
	{
		LocalPlayer player = WurstClient.MC.player;
		if(player == null)
			return "0.0 b/s";
		Vec3 velocity = player.getDeltaMovement();
		double speed = Math.hypot(velocity.x, velocity.z) * 20;
		return String.format(Locale.ROOT, "%.1f b/s", speed);
	}

	@Override
	public HudElementConfig getDefaultLayout()
	{
		return new HudElementConfig(HudElementConfig.HORIZONTAL_LEFT,
			HudElementConfig.VERTICAL_TOP, 3, 112);
	}
}
