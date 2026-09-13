/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.perimeter;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ported from the reference mod: the automation remembers the last state
 * changes together with the dimension and position they happened at, so that a
 * user can ask what the automation has been doing.
 */
public final class PerimeterStateHistory
{
	public static final int DEFAULT_LIMIT = 64;
	private static final int SHOWN_LIMIT = 16;
	
	private final int limit;
	private final ArrayDeque<PerimeterTransition> transitions =
		new ArrayDeque<>();
	
	public PerimeterStateHistory()
	{
		this(DEFAULT_LIMIT);
	}
	
	public PerimeterStateHistory(int limit)
	{
		this.limit = Math.max(1, limit);
	}
	
	public void record(PerimeterAutomationState previous,
		PerimeterAutomationState next, String detail, String dimension,
		String position)
	{
		transitions.addLast(new PerimeterTransition(Instant.now(), previous,
			next, detail, dimension, position));
		
		while(transitions.size() > limit)
			transitions.removeFirst();
	}
	
	public void clear()
	{
		transitions.clear();
	}
	
	public int size()
	{
		return transitions.size();
	}
	
	public List<PerimeterTransition> all()
	{
		return List.copyOf(transitions);
	}
	
	/**
	 * @return the newest entries first, as the history command shows them.
	 */
	public List<String> describe()
	{
		List<String> lines = new ArrayList<>();
		int shown = 0;
		
		var iterator = transitions.descendingIterator();
		
		while(iterator.hasNext() && shown < SHOWN_LIMIT)
		{
			lines.add(iterator.next().describe());
			shown++;
		}
		
		if(lines.isEmpty())
			lines.add("no state changes recorded yet");
		
		return lines;
	}
	
	public record PerimeterTransition(Instant at,
		PerimeterAutomationState previous, PerimeterAutomationState next,
		String detail, String dimension, String position)
	{
		public String describe()
		{
			return String.format(Locale.ROOT, "%s  %s -> %s  %s (%s, %s)",
				at.toString(), previous.id(), next.id(), detail, dimension,
				position);
		}
	}
}
