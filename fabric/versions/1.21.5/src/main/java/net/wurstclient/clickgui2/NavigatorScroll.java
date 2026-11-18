package net.wurstclient.clickgui2;

/** Rise wheel travel, 30px edge overscroll and millisecond-based smoothing. */
final class NavigatorScroll
{
	private float target;
	private float current;
	private float minimum;
	private long lastUpdate;
	private long lastWheel;

	void bounds(float contentHeight, float viewportHeight)
	{
		minimum = Math.min(0, viewportHeight - contentHeight);
	}

	void wheel(double delta, long now)
	{
		if(delta == 0)
			return;
		target = Math.max(minimum - 30, Math.min(30, target + (float)delta * 60));
		lastWheel = now;
	}

	float update(long now)
	{
		if(now - lastWheel >= 50)
			target = Math.max(minimum, Math.min(0, target));
		long elapsed = lastUpdate == 0 ? 16 : Math.max(0, now - lastUpdate);
		lastUpdate = now;
		current += (target - current) * (1 - (float)Math.pow(0.99, elapsed));
		if(Math.abs(target - current) < 0.01F)
			current = target;
		return current;
	}

	void reset()
	{
		target = current = 0;
		lastUpdate = 0;
	}

	float current()
	{
		return current;
	}

	float target() { return target; }
	void minimum(float value) { minimum = Math.min(0, value); }
	void snap(float interval) { target = Math.round(target / interval) * interval; }

	float minimum()
	{
		return minimum;
	}
}
