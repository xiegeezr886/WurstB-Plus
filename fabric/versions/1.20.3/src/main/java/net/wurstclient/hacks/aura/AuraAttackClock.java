/* Copyright (c) 2026 WurstB+ Plus contributors. SPDX-License-Identifier: AGPL-3.0-only */
package net.wurstclient.hacks.aura;

/** One sampled interval per attack, no render-frame queue or catch-up bursts. */
public final class AuraAttackClock
{
	private long nextAttack;
	private int lastTick = Integer.MIN_VALUE;

	public boolean ready(long now, int tick, boolean modern, float cooldown)
	{
		return tick != lastTick && (modern ? cooldown >= 1F
			: lastTick == Integer.MIN_VALUE || now >= nextAttack);
	}

	public void record(long now, int tick, double cps, double jitterMillis)
	{
		lastTick = tick;
		nextAttack = now + Math.max(50L,
			(long)Math.ceil(1000D / Math.max(1D, Math.min(20D, cps)) + jitterMillis));
	}

	public void reset() { nextAttack = 0; lastTick = Integer.MIN_VALUE; }
}
