/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package net.wurstclient.util;

import java.util.Objects;

/** One consumable plan tied to the player/world that produced it. */
public final class MotionActionQueue<T>
{
	private Object player;
	private Object world;
	private int tick;
	private T pending;

	public void begin(Object player, Object world, int tick)
	{
		clear();
		this.player = player;
		this.world = world;
		this.tick = tick;
	}

	public void schedule(T action)
	{
		pending = Objects.requireNonNull(action, "action");
	}

	public boolean isCurrent(Object player, Object world, int tick)
	{
		// Update is fired before the entity increments tickCount; Pre/PostMotion
		// are fired after it. A plan may cross that increment, but no more.
		long elapsed = (long)tick - this.tick;
		return pending != null && player != null && world != null
			&& this.player == player && this.world == world
			&& elapsed >= 0 && elapsed <= 1;
	}

	public T consume(Object player, Object world, int tick)
	{
		T result = isCurrent(player, world, tick) ? pending : null;
		clear();
		return result;
	}

	public void clear()
	{
		pending = null;
		player = null;
		world = null;
		tick = -1;
	}
}
