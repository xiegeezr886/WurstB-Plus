package net.wurstclient.clickgui2.animation;

public final class HoverAnimation
{
	private static final float MAX_FRAME_TIME = 0.05F;

	private final float speed;
	private long lastUpdateNanos;
	private float value;

	public HoverAnimation()
	{
		this(18);
	}

	public HoverAnimation(float speed)
	{
		this.speed = Math.max(1, speed);
	}

	public float update(boolean active)
	{
		long now = System.nanoTime();
		if(lastUpdateNanos == 0)
		{
			lastUpdateNanos = now;
			value = active ? 1 : 0;
			return value;
		}

		float frameTime = Math.min(MAX_FRAME_TIME,
			(now - lastUpdateNanos) / 1_000_000_000F);
		lastUpdateNanos = now;
		float target = active ? 1 : 0;
		float blend = 1 - (float)Math.exp(-speed * frameTime);
		value += (target - value) * blend;
		if(Math.abs(target - value) < 0.002F)
			value = target;
		return value;
	}
}
