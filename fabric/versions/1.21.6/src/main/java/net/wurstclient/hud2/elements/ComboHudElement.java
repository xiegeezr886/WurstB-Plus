package net.wurstclient.hud2.elements;

import net.minecraft.world.entity.Entity;
import net.wurstclient.WurstClient;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.hud2.HudManager;

public final class ComboHudElement extends TextHudElement
	implements PlayerAttacksEntityListener
{
	private Entity lastTarget;
	private int combo;
	private long lastHitNanos;
	private static final long COMBO_TIMEOUT_NANOS = 3_000_000_000L;

	public ComboHudElement()
	{
		super("combo", "Combo");
		setDefaultPosition(3, 188);
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
		lastTarget = null;
		combo = 0;
		lastHitNanos = 0;
	}

	@Override
	public void onPlayerAttacksEntity(Entity target)
	{
		long now = System.nanoTime();
		combo = target == lastTarget
			&& now - lastHitNanos <= COMBO_TIMEOUT_NANOS ? combo + 1 : 1;
		lastTarget = target;
		lastHitNanos = now;
	}

	@Override
	public String getText()
	{
		if(lastHitNanos == 0
			|| System.nanoTime() - lastHitNanos > COMBO_TIMEOUT_NANOS)
			combo = 0;
		return "Combo: " + combo;
	}
}
