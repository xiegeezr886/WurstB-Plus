/* Adapted from OpaiBounce motion.ts (Morph); see assets/wurstpenguin/opai/NOTICE.txt. */
package net.wurstclient.hud2.island;

/** Exact damped spring. Retargeting retains velocity, including mid-flight reversal. */
public final class IslandMotion
{
	private double current, target, velocity;
	private long sampled = -1;

	public float value(double destination, long now, double impulse)
	{
		if(sampled < 0)
		{
			current = target = destination;
			velocity = 0;
		}else
		{
			double seconds = Math.max(0, now - sampled) / 1000.0;
			double displacement = current - target;
			double coefficient = (velocity + 8.7 * displacement) / 8;
			double cosine = Math.cos(8 * seconds), sine = Math.sin(8 * seconds);
			double decay = Math.exp(-8.7 * seconds);
			double offset = displacement * cosine + coefficient * sine;
			current = target + decay * offset;
			velocity = decay * (-8.7 * offset
				+ 8 * (-displacement * sine + coefficient * cosine));
			if(destination != target)
			{
				target = destination;
				if(Math.abs(velocity) < Math.max(.1, Math.abs(target - current) * .1))
					velocity = (target - current) * impulse;
			}
			if(Math.abs(current - target) < .01 && Math.abs(velocity) < .1)
			{
				current = target;
				velocity = 0;
			}
		}
		sampled = now;
		return (float)current;
	}

	public void reset()
	{
		sampled = -1;
	}
}
