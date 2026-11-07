package net.wurstclient.clickgui2.animation;

/**
 * Frame-rate independent exponential smoothing for scroll offsets.
 *
 * <p>The target is the offset the user scrolled to; the current value follows
 * it with a short ease-out so wheel input never snaps the content by a whole
 * row.
 */
public final class SmoothScroll
{
	private static final float MAX_FRAME_TIME = 0.05F;

	private final float speed;
	private float current;
	private float target;
	private long lastUpdateNanos;

	public SmoothScroll()
	{
		this(22);
	}

	public SmoothScroll(float speed)
	{
		this.speed = Math.max(1, speed);
	}

	public void setTarget(float value)
	{
		target = value;
	}

	public void snapTo(float value)
	{
		target = value;
		current = value;
		lastUpdateNanos = 0;
	}

	public float getCurrent()
	{
		return current;
	}

	public float getTarget()
	{
		return target;
	}

	public boolean isAnimating()
	{
		return Math.abs(target - current) > 0.01F;
	}

	public float update()
	{
		long now = System.nanoTime();
		if(lastUpdateNanos == 0)
		{
			lastUpdateNanos = now;
			current = target;
			return current;
		}

		float frameTime = Math.min(MAX_FRAME_TIME,
			(now - lastUpdateNanos) / 1_000_000_000F);
		lastUpdateNanos = now;

		float blend = 1 - (float)Math.exp(-speed * frameTime);
		current += (target - current) * blend;
		if(Math.abs(target - current) < 0.01F)
			current = target;
		return current;
	}
}
