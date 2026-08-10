/*
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

public enum AttackTimerPolicy
{
	;

	private static final double NANOS_PER_MILLI = 1_000_000D;
	private static final long MAX_DELAY_NANOS = Long.MAX_VALUE - 1;

	public static long delayNanos(double cps, double gaussianSample,
		double maximumRandomMillis)
	{
		double safeCps = Double.isFinite(cps) && cps > 0 ? cps : 0;
		double safeSample = Double.isFinite(gaussianSample) ? gaussianSample : 0;
		double safeRandom = Double.isFinite(maximumRandomMillis)
			&& maximumRandomMillis > 0 ? maximumRandomMillis : 0;
		double delayMillis = safeCps > 0 ? 1000 / safeCps : 0;
		delayMillis = Math.max(0,
			delayMillis + safeSample * safeRandom);
		if(!Double.isFinite(delayMillis)
			|| delayMillis >= MAX_DELAY_NANOS / NANOS_PER_MILLI)
			return MAX_DELAY_NANOS;
		return (long)(delayMillis * NANOS_PER_MILLI);
	}

	public static long deadline(long nowNanos, long delayNanos)
	{
		long safeDelay = Math.max(0, Math.min(MAX_DELAY_NANOS, delayNanos));
		return nowNanos + safeDelay;
	}

	public static boolean isElapsed(long nowNanos, long deadlineNanos)
	{
		return nowNanos - deadlineNanos >= 0;
	}
}
