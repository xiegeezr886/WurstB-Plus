/*
 * Modern mouse-wheel port of Rise 6.1.30's ScrollUtil.
 */
package net.wurstclient.clickgui2;

import net.minecraft.util.Mth;

final class RiseScrollState
{
	private double target;
	private double scroll;
	private double minimum;
	private long lastNanos = System.nanoTime();
	private long overscrollUntilNanos;

	double update()
	{
		long now = System.nanoTime();
		if(now >= overscrollUntilNanos)
			target = Mth.clamp(target, minimum, 0);
		double elapsedMillis = Math.min(50, (now - lastNanos) / 1_000_000D);
		lastNanos = now;
		double amount = 1 - Math.pow(0.99, elapsedMillis);
		scroll += (target - scroll) * amount;
		if(Math.abs(scroll - target) < 0.05)
			scroll = target;
		return scroll;
	}

	void wheel(double delta)
	{
		target = Mth.clamp(target + delta * 60, minimum - 30, 30);
		overscrollUntilNanos = System.nanoTime() + 50_000_000L;
	}

	void setMinimum(double minimum)
	{
		this.minimum = Math.min(0, minimum);
		if(System.nanoTime() >= overscrollUntilNanos)
			target = Mth.clamp(target, this.minimum, 0);
	}

	void dragTo(double value)
	{
		target = scroll = Mth.clamp(value, minimum, 0);
	}

	double scroll()
	{
		return scroll;
	}

	double minimum()
	{
		return minimum;
	}

	double target()
	{
		return target;
	}

	void reset()
	{
		target = 0;
		scroll = 0;
		lastNanos = System.nanoTime();
		overscrollUntilNanos = 0;
	}
}
