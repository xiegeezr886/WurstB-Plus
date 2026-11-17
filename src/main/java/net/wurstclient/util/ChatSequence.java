/*
 * AutoMessage sequence syntax adapted from InvincibleMachineGun (GPL-3.0).
 * SPDX-License-Identifier: GPL-3.0
 */
package net.wurstclient.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** Parses the IMG selected-prefix / trailing-delay syntax independently of chat IO. */
public final class ChatSequence
{
	private int sent;

	public void reset()
	{
		sent = 0;
	}

	public Entry next(List<String> lines, boolean selectedOnly, String prefix,
		int defaultSeconds, boolean loop, boolean random, IntUnaryOperator randomIndex)
	{
		List<Entry> entries = parse(lines, selectedOnly, prefix, defaultSeconds);
		if(entries.isEmpty() || !loop && sent >= entries.size())
			return null;
		int index = random ? randomIndex.applyAsInt(entries.size()) : sent % entries.size();
		sent++;
		if(loop && sent >= entries.size())
			sent = 0;
		return entries.get(index);
	}

	public static List<Entry> parse(List<String> lines, boolean selectedOnly,
		String prefix, int defaultSeconds)
	{
		List<Entry> result = new ArrayList<>();
		for(String line : lines)
		{
			if(selectedOnly && !line.startsWith(prefix))
				continue;
			String content = (selectedOnly ? line.substring(prefix.length()) : line).trim();
			if(content.isEmpty())
				continue;
			int delay = Math.max(0, defaultSeconds);
			int hash = content.lastIndexOf('#');
			if(hash >= 0)
				try
				{
					delay = Math.max(0, Integer.parseInt(content.substring(hash + 1).trim()));
					content = content.substring(0, hash).trim();
				}catch(NumberFormatException ignored)
				{
					// Non-numeric suffixes are ordinary message text.
				}
			long ticks = (long)delay * 20;
			result.add(new Entry(content, (int)Math.min(ticks, Integer.MAX_VALUE)));
		}
		return result;
	}

	public record Entry(String message, int waitTicks)
	{
	}
}
