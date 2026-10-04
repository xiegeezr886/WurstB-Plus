/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

/**
 * Frame timing for an animated background.
 *
 * <p>
 * Pure arithmetic on the frame delays, so the playback rules can be tested
 * without decoding anything, and the renderer only ever asks "which frame does
 * this wall clock belong to".
 *
 * <p>
 * Degenerate delays are handled here rather than in the renderer: browsers treat
 * a 0 or 10 ms GIF delay as 100 ms, and without that rule a two-frame GIF with
 * zero delays would ask for an upload every single frame.
 */
public final class BackgroundAnimation
{
	/** Delays below this are treated as {@link #DEFAULT_DELAY_MS}. */
	public static final int MIN_USEFUL_DELAY_MS = 20;

	/** The delay a degenerate frame is given, matching the browser rule. */
	public static final int DEFAULT_DELAY_MS = 100;

	/** Nothing is gained by a delay longer than this. */
	public static final int MAX_DELAY_MS = 10_000;

	private final int[] delaysMs;
	private final int loopCount;

	/**
	 * @param delaysMs
	 *            one delay per frame, in milliseconds
	 * @param loopCount
	 *            how often the animation repeats, {@code 0} for forever
	 */
	public BackgroundAnimation(int[] delaysMs, int loopCount)
	{
		if(delaysMs == null || delaysMs.length == 0)
			throw new IllegalArgumentException(
				"an animation needs at least one frame");

		this.delaysMs = new int[delaysMs.length];

		for(int i = 0; i < delaysMs.length; i++)
			this.delaysMs[i] = sanitize(delaysMs[i]);

		this.loopCount = Math.max(0, loopCount);
	}

	public static int sanitize(int delayMs)
	{
		if(delayMs < MIN_USEFUL_DELAY_MS)
			return DEFAULT_DELAY_MS;

		return Math.min(delayMs, MAX_DELAY_MS);
	}

	public int frameCount()
	{
		return delaysMs.length;
	}

	/** {@code 0} means the animation repeats forever. */
	public int loopCount()
	{
		return loopCount;
	}

	public boolean repeatsForever()
	{
		return loopCount == 0;
	}

	/** How long one pass through every frame takes. */
	public long totalDurationMs()
	{
		long total = 0;

		for(int delay : delaysMs)
			total += delay;

		return total;
	}

	/**
	 * @param elapsedMs
	 *            time since the animation started
	 * @return the frame that belongs to that moment. A finite animation holds
	 *         its last frame once it has played through, and a single frame
	 *         always answers {@code 0}.
	 */
	public int frameIndexAt(long elapsedMs)
	{
		if(delaysMs.length == 1)
			return 0;

		long elapsed = Math.max(0L, elapsedMs);
		long total = totalDurationMs();

		if(!repeatsForever())
		{
			long playtime = total * loopCount;

			if(elapsed >= playtime)
				return delaysMs.length - 1;
		}

		long offset = elapsed % total;
		long walked = 0;

		for(int i = 0; i < delaysMs.length; i++)
		{
			walked += delaysMs[i];

			if(offset < walked)
				return i;
		}

		return delaysMs.length - 1;
	}
}
