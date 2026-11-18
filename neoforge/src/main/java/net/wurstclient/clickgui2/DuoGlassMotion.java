package net.wurstclient.clickgui2;

/** WindowsDuo's 0.22-per-frame angle smoothing, normalized to 60 Hz. */
final class DuoGlassMotion
{
	private double fold = 1;
	private long sampled;
	private boolean closing;

	DuoGlassMotion(long now) { sampled = now; }

	float value(long now)
	{
		double target = closing ? 1 : 0;
		long elapsed = Math.max(0, now - sampled);
		fold = target + (fold - target) * Math.pow(.78, elapsed * .06);
		if(Math.abs(fold - target) < .005) fold = target;
		sampled = Math.max(sampled, now);
		return (float)fold;
	}

	void close(long now)
	{
		value(now);
		closing = true;
	}

	boolean ready(long now) { return !closing && value(now) == 0; }
	boolean finished(long now) { return closing && value(now) == 1; }
	boolean closing() { return closing; }
}
