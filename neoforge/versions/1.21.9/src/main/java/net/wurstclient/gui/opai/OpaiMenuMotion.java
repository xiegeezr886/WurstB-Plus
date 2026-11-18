package net.wurstclient.gui.opai;

/** Opai menuMotion.ts: quint-out rotation (700ms), phase (630/840ms). */
public final class OpaiMenuMotion
{
	private static OpaiMenuMotion handoff;
	private static long handedAt;
	private final boolean toList;
	private final double fromRotation, fromPhase;
	private long started;
	private boolean handed;

	public OpaiMenuMotion(boolean toList, double rotation, double phase, long now)
	{
		this.toList = toList;
		fromRotation = rotation;
		fromPhase = phase;
		started = now;
	}

	public static OpaiMenuMotion enter(boolean list, long now)
	{
		if(handoff != null && handoff.toList == list && now - handedAt < 5000)
		{
			OpaiMenuMotion result = handoff;
			handoff = null;
			result.started += now - handedAt;
			return result;
		}
		handoff = null;
		return list ? new OpaiMenuMotion(true, -90, .79, now) : null;
	}

	public double rotation(long now) { return tween(fromRotation, toList ? -180 : 0, now, 700); }
	public double phase(long now) { return tween(fromPhase, toList ? 1 : 0, now, toList ? 630 : 840); }
	public float fade(long now) { return (float)Math.max(0, Math.min(1, (phase(now) - .79) / .21)); }
	public boolean finished(long now) { return now - started >= (toList ? 700 : 840); }
	public boolean crossed(long now) { return toList ? rotation(now) <= -90 : rotation(now) >= -90; }

	public void handoff(long now)
	{
		if(handed) return;
		handed = true;
		double edge = (-90 - fromRotation) / ((toList ? -180 : 0) - fromRotation);
		long elapsed = Math.round(700 * (1 - Math.pow(1 - Math.max(0, Math.min(1, edge)), .2)));
		started = now - elapsed;
		handoff = this;
		handedAt = now;
	}

	private double tween(double from, double target, long now, int duration)
	{
		double t = Math.max(0, Math.min(1, (now - started) / (double)duration));
		return from + (target - from) * (1 - Math.pow(1 - t, 5));
	}

	public static float[] projection(double angle, double scale)
	{
		double radians = Math.toRadians(angle);
		return new float[]{(float)(Math.abs(Math.cos(radians)) * scale),
			(float)(Math.sin(radians) * .08 * scale), (float)scale};
	}
}
