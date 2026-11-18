package net.wurstclient.hud2.target;

/** Time-based target-card motion, independent of Minecraft and frame rate. */
public final class TargetHudMotion
{
	private boolean visible;
	private long changedAt;
	private float from;
	private float healthFrom;
	private float healthTo;
	private long healthChangedAt;
	private boolean initialized;

	public void setVisible(boolean value, long now)
	{
		if(visible == value)
			return;
		from = scale(now);
		visible = value;
		changedAt = now;
	}

	public float scale(long now)
	{
		double t = Math.max(0, Math.min(1,
			(now - changedAt) / (visible ? 850D : 400D)));
		double eased;
		if(t == 0 || t == 1)
			eased = t;
		else if(visible)
			eased = 1 + Math.pow(2, -10 * t)
				* Math.sin((t * 10 - 0.75) * (2 * Math.PI / 3));
		else
			eased = 2.70158 * t * t * t - 1.70158 * t * t;
		return (float)Math.max(0, from + ((visible ? 1 : 0) - from) * eased);
	}

	public void setHealth(float health, float maximum, boolean newTarget,
		long now)
	{
		float ratio = Float.isFinite(health) && Float.isFinite(maximum)
			&& maximum > 0 ? Math.max(0, Math.min(1, health / maximum)) : 0;
		if(newTarget || !initialized)
		{
			healthFrom = 0;
			healthTo = ratio;
			healthChangedAt = now;
			initialized = true;
		}else if(ratio != healthTo)
		{
			healthFrom = health(now);
			healthTo = ratio;
			healthChangedAt = now;
		}
	}

	public float health(long now)
	{
		double t = Math.max(0, Math.min(1, (now - healthChangedAt) / 250D));
		return (float)(healthTo + (healthFrom - healthTo) * Math.pow(1 - t, 5));
	}

	public static boolean shouldShow(long now, long lastAttack, boolean valid)
	{
		return valid && now - lastAttack < 1000;
	}
}
