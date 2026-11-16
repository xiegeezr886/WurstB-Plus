/* Copyright (c) 2026 WurstB+ Plus contributors. SPDX-License-Identifier: AGPL-3.0-only */
package net.wurstclient.hacks.aura;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** NeoRaven's identity-based cycle with Epsilon's timed Single/Switch modes. */
public final class AuraTargetCycle<T>
{
	private T current;
	private long selectedAt;
	private final Set<T> visited = new HashSet<>();

	/** Input is already ordered by priority; reordering must not restart a hold. */
	public T select(List<T> ordered, boolean switching, long delay, long now)
	{
		if(ordered.isEmpty()) { reset(); return null; }
		visited.retainAll(ordered);
		if(!switching)
		{
			current = ordered.get(0);
			selectedAt = now;
			visited.clear();
			visited.add(current);
			return current;
		}
		if(current != null && ordered.contains(current) && now >= selectedAt
			&& now - selectedAt < Math.max(0, delay)) return current;
		T previous = current;
		current = ordered.stream().filter(id -> !visited.contains(id))
			.findFirst().orElse(null);
		if(current == null)
		{
			visited.clear();
			current = ordered.stream().filter(id -> !id.equals(previous))
				.findFirst().orElse(ordered.get(0));
		}
		visited.add(current);
		selectedAt = now;
		return current;
	}

	public void reset() { current = null; selectedAt = 0; visited.clear(); }
}
