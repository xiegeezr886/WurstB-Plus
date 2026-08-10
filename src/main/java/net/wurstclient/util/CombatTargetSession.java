/*
 * Copyright (c) 2015-2026 CCBlueX
 * Copyright (c) 2025-2026 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Owns target continuity and reports identity changes to module state machines.
 */
public final class CombatTargetSession<T>
{
	private final TargetTracker<T> tracker = new TargetTracker<>();
	private T target;
	private long generation;

	public void tick()
	{
		tracker.tick();
	}

	public Selection<T> update(T candidate, Predicate<T> validator,
		ToDoubleFunction<T> score, boolean sticky, int switchDelay,
		double switchAdvantagePercent)
	{
		T selected = tracker.update(candidate, validator, score, sticky,
			switchDelay, switchAdvantagePercent);
		return apply(selected);
	}

	public Selection<T> track(T selected)
	{
		tracker.reset();
		return apply(selected);
	}

	public Selection<T> clear()
	{
		tracker.reset();
		return apply(null);
	}

	private Selection<T> apply(T selected)
	{
		T previous = target;
		boolean changed = previous != selected;
		target = selected;
		if(changed)
			generation++;
		return new Selection<>(previous, selected, changed, generation);
	}

	public T getTarget()
	{
		return target;
	}

	public long getGeneration()
	{
		return generation;
	}

	public record Selection<T>(T previous, T current, boolean changed,
		long generation)
	{
	}
}
