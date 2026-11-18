package net.wurstclient.gui.opai;

/** The title disappears completely before the destination starts its flip. */
public record OpaiMenuFade(boolean entering, long started)
{
	public static final int DURATION_MS = 240;

	public float opacity(long now)
	{
		double t = Math.max(0, Math.min(1, (now - started) / (double)DURATION_MS));
		float eased = (float)(t * t * (3 - 2 * t));
		return entering ? eased : 1 - eased;
	}

	public boolean finished(long now)
	{
		return now - started >= DURATION_MS;
	}
}
