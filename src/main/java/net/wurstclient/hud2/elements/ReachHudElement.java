package net.wurstclient.hud2.elements;

import java.util.Locale;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hud2.HudLayout.HudElementConfig;
import net.wurstclient.hud2.HudManager;

public final class ReachHudElement extends SoarTextHudElement
	implements PlayerAttacksEntityListener
{
	private static final long DISPLAY_NANOS = 5_000_000_000L;
	private double distance;
	private long attackedAtNanos;

	public ReachHudElement()
	{
		super("reach_display", "\u653b\u51fb\u8ddd\u79bb",
			HudElementConfig.HORIZONTAL_RIGHT, 110, 200);
	}

	@Override
	public void onEnable(HudManager manager)
	{
		WurstClient.INSTANCE.getEventManager()
			.add(PlayerAttacksEntityListener.class, this);
	}

	@Override
	public void onDisable(HudManager manager)
	{
		WurstClient.INSTANCE.getEventManager()
			.remove(PlayerAttacksEntityListener.class, this);
		distance = 0;
		attackedAtNanos = 0;
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		LocalPlayer player = WurstClient.MC.player;
		if(player == null)
			return;
		Vec3 eyes = player.getEyePosition(1);
		Vec3 hit = null;
		if(WurstClient.MC.hitResult instanceof EntityHitResult result
			&& result.getEntity() == target)
			hit = result.getLocation();
		if(hit == null)
		{
			AABB box = target.getBoundingBox();
			hit = new Vec3(Mth.clamp(eyes.x, box.minX, box.maxX),
				Mth.clamp(eyes.y, box.minY, box.maxY),
				Mth.clamp(eyes.z, box.minZ, box.maxZ));
		}
		distance = eyes.distanceTo(hit);
		attackedAtNanos = System.nanoTime();
	}

	@Override
	protected String getText()
	{
		if(attackedAtNanos == 0
			|| System.nanoTime() - attackedAtNanos > DISPLAY_NANOS)
			return "Hasn't attacked";
		return String.format(Locale.ROOT, "%.2f blocks", distance);
	}
}
