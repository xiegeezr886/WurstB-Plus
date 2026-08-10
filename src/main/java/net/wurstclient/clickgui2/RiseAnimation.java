/*
 * API-compatible port of Rise 6.1.30's Animation behavior.
 */
package net.wurstclient.clickgui2;

final class RiseAnimation
{
	enum Easing
	{
		LINEAR,
		EASE_IN_EXPO,
		EASE_OUT_EXPO
	}

	private Easing easing;
	private long durationNanos;
	private float startValue;
	private float destinationValue;
	private float value;
	private long startNanos;

	RiseAnimation(Easing easing, int durationMillis)
	{
		this.easing = easing;
		setDuration(durationMillis);
		startNanos = System.nanoTime();
	}

	float run(float destination)
	{
		long now = System.nanoTime();
		advance(now);
		if(Float.compare(destinationValue, destination) != 0)
		{
			startValue = value;
			destinationValue = destination;
			startNanos = now;
		}
		advance(now);
		return value;
	}

	void setValue(float value)
	{
		this.value = value;
		startValue = value;
		destinationValue = value;
		startNanos = System.nanoTime();
	}

	void setDuration(int durationMillis)
	{
		durationNanos = Math.max(1, durationMillis) * 1_000_000L;
	}

	void setEasing(Easing easing)
	{
		this.easing = easing;
	}

	private void advance(long now)
	{
		if(Float.compare(value, destinationValue) == 0)
			return;
		float progress = Math.min(1,
			Math.max(0, (now - startNanos) / (float)durationNanos));
		float eased = switch(easing)
		{
			case LINEAR -> progress;
			case EASE_IN_EXPO -> progress == 0 ? 0
				: (float)Math.pow(2, 10 * progress - 10);
			case EASE_OUT_EXPO -> progress == 1 ? 1
				: 1 - (float)Math.pow(2, -10 * progress);
		};
		value = startValue + (destinationValue - startValue) * eased;
		if(progress >= 1)
			value = destinationValue;
	}
}
