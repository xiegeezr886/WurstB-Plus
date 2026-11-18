package net.wurstclient.clickgui2;

/** Time-based ease-out used by the Rise-style navigator. */
final class NavigatorAnimation
{
	private float from;
	private float target;
	private long started;
	private long duration;
	private Easing easing = Easing.OUT_EXPO;

	enum Easing { OUT_EXPO, LINEAR, OUT_QUINT }

	NavigatorAnimation(float value)
	{
		from = target = value;
	}

	float value(long now)
	{
		if(duration == 0 || now - started >= duration)
			return target;
		float progress = Math.max(0, (now - started) / (float)duration);
		float remaining = switch(easing) {
			case LINEAR -> 1 - progress;
			case OUT_QUINT -> (float)Math.pow(1 - progress, 5);
			case OUT_EXPO -> (float)Math.pow(2, -10 * progress);
		};
		return target + (from - target) * remaining;
	}

	void animate(float value, long millis, long now)
	{
		animate(value, millis, now, Easing.OUT_EXPO);
	}

	void animate(float value, long millis, long now, Easing curve)
	{
		if(target == value)
			return;
		from = value(now);
		easing = curve;
		target = value;
		started = now;
		duration = millis;
	}

	void set(float value) { from = target = value; duration = 0; }
}
