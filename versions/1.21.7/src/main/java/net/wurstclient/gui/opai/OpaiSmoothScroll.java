package net.wurstclient.gui.opai;

/** Time-based scrolling shared by worlds, servers and accounts. */
public final class OpaiSmoothScroll
{
	private double position, target, maximum;
	private long lastFrame = -1;

	public double position() { return position; }
	public double target() { return target; }

	public void limit(double maximum)
	{
		this.maximum = Math.max(0, maximum);
		position = clamp(position);
		target = clamp(target);
	}

	public void by(double delta) { target = clamp(target + delta); }

	public void reveal(double top, double bottom, double viewport)
	{
		if(top < target) target = clamp(top);
		else if(bottom > target + viewport) target = clamp(bottom - viewport);
	}

	public void step(long now)
	{
		if(lastFrame >= 0)
		{
			double elapsed = Math.max(0, now - lastFrame);
			position += (target - position) * -Math.expm1(-elapsed / 70D);
			if(Math.abs(target - position) < .01) position = target;
		}
		lastFrame = now;
	}

	private double clamp(double value)
	{
		return Math.max(0, Math.min(maximum, value));
	}
}
