package net.wurstclient.clickgui2.supersoft;

public final class UiMotion
{
	private static final float MAX_FRAME_TIME = 0.05F;

	private final float stiffness;
	private final float damping;
	private float value;
	private float velocity;
	private long lastUpdateNanos;

	public UiMotion(float initialValue)
	{
		this(initialValue, 300, 0.8F);
	}

	public UiMotion(float initialValue, float stiffness, float damping)
	{
		value = initialValue;
		this.stiffness = Math.max(1, stiffness);
		this.damping = Math.max(0.05F, damping);
	}

	public float update(float target)
	{
		long now = System.nanoTime();
		if(lastUpdateNanos == 0)
		{
			lastUpdateNanos = now;
			return value;
		}
		float delta = Math.min(MAX_FRAME_TIME,
			(now - lastUpdateNanos) / 1_000_000_000F);
		lastUpdateNanos = now;
		float acceleration = (target - value) * stiffness
			- velocity * 2F * damping * (float)Math.sqrt(stiffness);
		velocity += acceleration * delta;
		value += velocity * delta;
		if(Math.abs(target - value) < 0.001F && Math.abs(velocity) < 0.01F)
		{
			value = target;
			velocity = 0;
		}
		return value;
	}

	public float get()
	{
		return value;
	}

	public void snap(float value)
	{
		this.value = value;
		velocity = 0;
		lastUpdateNanos = 0;
	}
}
