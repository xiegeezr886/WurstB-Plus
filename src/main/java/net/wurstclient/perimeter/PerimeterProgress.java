/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.util.Locale;

/**
 * Tracks the excavation progress of a planned {@link PerimeterArea}: how many
 * blocks were there to begin with, how many are left, how long it has been
 * running and how much longer it is likely to take.
 *
 * <p>
 * All timing is passed in as nanoseconds so that this class stays free of
 * Minecraft types and can be unit tested deterministically.
 */
public final class PerimeterProgress
{
	private int total;
	private int remaining;
	
	private long startedAtNanos = -1;
	private long finishedAtNanos = -1;
	
	public void begin(int totalBlocks, long nowNanos)
	{
		total = Math.max(0, totalBlocks);
		remaining = total;
		startedAtNanos = nowNanos;
		finishedAtNanos = -1;
	}
	
	public void updateRemaining(int remainingBlocks)
	{
		remaining = Math.max(0, Math.min(total, remainingBlocks));
	}
	
	public void finish(long nowNanos)
	{
		remaining = 0;
		finishedAtNanos = nowNanos;
	}
	
	public void reset()
	{
		total = 0;
		remaining = 0;
		startedAtNanos = -1;
		finishedAtNanos = -1;
	}
	
	public boolean isStarted()
	{
		return startedAtNanos >= 0;
	}
	
	public boolean isFinished()
	{
		return finishedAtNanos >= 0;
	}
	
	public boolean isComplete()
	{
		return isStarted() && remaining == 0;
	}
	
	public int total()
	{
		return total;
	}
	
	public int remaining()
	{
		return remaining;
	}
	
	/**
	 * Number of blocks that are gone already.
	 */
	public int broken()
	{
		return Math.max(0, total - remaining);
	}
	
	/**
	 * Excavated fraction, from 0 to 1.
	 */
	public double fraction()
	{
		if(total <= 0)
			return 1;
		
		double fraction = (double)broken() / (double)total;
		return Math.max(0, Math.min(1, fraction));
	}
	
	public int percent()
	{
		return (int)Math.round(fraction() * 100);
	}
	
	/**
	 * Milliseconds since {@link #begin(int, long)}, frozen after
	 * {@link #finish(long)}.
	 */
	public long elapsedMillis(long nowNanos)
	{
		if(startedAtNanos < 0)
			return 0;
		
		long end = finishedAtNanos >= 0 ? finishedAtNanos : nowNanos;
		return Math.max(0, (end - startedAtNanos) / 1000000L);
	}
	
	/**
	 * Estimated milliseconds until the region is fully excavated, or -1 while
	 * nothing has been broken yet and the rate is still unknown.
	 */
	public long etaMillis(long nowNanos)
	{
		if(!isStarted() || isComplete())
			return isComplete() ? 0 : -1;
		
		int broken = broken();
		if(broken <= 0)
			return -1;
		
		long elapsed = elapsedMillis(nowNanos);
		if(elapsed <= 0)
			return -1;
		
		return (long)((double)elapsed / (double)broken * remaining);
	}
	
	/**
	 * Formats a duration as {@code 45s}, {@code 3m 05s} or {@code 1h 02m 03s}.
	 */
	public static String formatDuration(long millis)
	{
		if(millis < 0)
			return "unknown";
		
		long totalSeconds = millis / 1000L;
		long hours = totalSeconds / 3600L;
		long minutes = totalSeconds % 3600L / 60L;
		long seconds = totalSeconds % 60L;
		
		if(hours > 0)
			return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes,
				seconds);
		
		if(minutes > 0)
			return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds);
		
		return totalSeconds + "s";
	}
	
	/**
	 * One-line summary, e.g.
	 * {@code 1234/5678 blocks (21%), 5m 12s elapsed, ~20m 03s left}.
	 */
	public String describe(long nowNanos)
	{
		StringBuilder builder = new StringBuilder();
		builder.append(broken()).append('/').append(total).append(" blocks (")
			.append(percent()).append("%)");
		
		if(isStarted())
		{
			builder.append(", ").append(formatDuration(elapsedMillis(nowNanos)))
				.append(isFinished() ? " total" : " elapsed");
			
			long eta = etaMillis(nowNanos);
			if(eta > 0)
				builder.append(", ~").append(formatDuration(eta)).append(" left");
		}
		
		return builder.toString();
	}
}
