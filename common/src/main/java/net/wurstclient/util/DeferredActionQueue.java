/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.wurstclient.WurstClient;
import net.wurstclient.events.UpdateListener;

public final class DeferredActionQueue implements UpdateListener
{
	private final Map<String, List<Entry>> queues = new LinkedHashMap<>();
	private boolean registered;

	public synchronized void add(String queueId, Runnable action,
		int delayTicks)
	{
		queues.computeIfAbsent(queueId, k -> new ArrayList<>())
			.add(new Entry(action, delayTicks));

		if(!registered)
		{
			WurstClient.INSTANCE.getEventManager()
				.add(UpdateListener.class, this);
			registered = true;
		}
	}

	public synchronized void cancelQueue(String queueId)
	{
		queues.remove(queueId);
		if(queues.isEmpty() && registered)
		{
			WurstClient.INSTANCE.getEventManager()
				.remove(UpdateListener.class, this);
			registered = false;
		}
	}

	public void runAllInQueue(String queueId)
	{
		List<Entry> entries;
		synchronized(this)
		{
			entries = queues.remove(queueId);
			if(queues.isEmpty() && registered)
			{
				WurstClient.INSTANCE.getEventManager()
					.remove(UpdateListener.class, this);
				registered = false;
			}
		}

		if(entries != null)
			for(Entry entry : entries)
				entry.action.run();
	}

	@Override
	public void onUpdate()
	{
		if(Minecraft.getInstance().isPaused())
			return;

		List<Runnable> ready;
		synchronized(this)
		{
			ready = new ArrayList<>();
			queues.values().removeIf(entryList -> {
				entryList.removeIf(entry -> {
					if(--entry.remainingTicks <= 0)
					{
						ready.add(entry.action);
						return true;
					}
					return false;
				});
				return entryList.isEmpty();
			});

			if(queues.isEmpty() && registered)
			{
				WurstClient.INSTANCE.getEventManager()
					.remove(UpdateListener.class, this);
				registered = false;
			}
		}

		for(Runnable action : ready)
			action.run();
	}

	private static class Entry
	{
		final Runnable action;
		int remainingTicks;

		Entry(Runnable action, int remainingTicks)
		{
			this.action = action;
			this.remainingTicks = remainingTicks;
		}
	}
}
