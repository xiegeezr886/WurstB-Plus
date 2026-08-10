package net.wurstclient.clickgui2.supersoft;

public final class UiTween
{
	private final long durationNanos;
	private final boolean linear;
	private float value;
	private float startValue;
	private float target;
	private long startNanos;

	public UiTween(float initialValue, int durationMillis)
	{
		this(initialValue, durationMillis, false);
	}

	public UiTween(float initialValue, int durationMillis, boolean linear)
	{
		value = initialValue;
		startValue = initialValue;
		target = initialValue;
		durationNanos = Math.max(1, durationMillis) * 1_000_000L;
		this.linear = linear;
	}

	public float update(float newTarget)
	{
		long now = System.nanoTime();
		advance(now);
		if(Float.compare(target, newTarget) != 0)
		{
			startValue = value;
			target = newTarget;
			startNanos = now;
		}
		advance(now);
		return value;
	}

	private void advance(long now)
	{
		if(Float.compare(value, target) == 0)
			return;
		float elapsed = Math.min(1,
			(now - startNanos) / (float)durationNanos);
		float progress = linear ? elapsed
			: elapsed * elapsed * (3 - 2 * elapsed);
		value = startValue + (target - startValue) * progress;
		if(elapsed >= 1)
			value = target;
	}

	public float get()
	{
		return value;
	}

	public void snap(float value)
	{
		this.value = value;
		startValue = value;
		target = value;
		startNanos = 0;
	}
}
