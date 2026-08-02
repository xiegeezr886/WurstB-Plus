/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.event;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.LongAdder;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.wurstclient.WurstClient;

public final class EventManager
{
	private final WurstClient wurst;
	private final HashMap<Class<? extends Listener>, ArrayList<? extends Listener>> listenerMap =
		new HashMap<>();
	private final ConcurrentHashMap<Class<? extends Listener>, ArrayList<? extends Listener>> listenerSnapshots =
		new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Class<? extends Listener>, LongAdder> eventCounters =
		new ConcurrentHashMap<>();
	private final LongAdder totalEventsFired = new LongAdder();

	private final Map<Class<?>, List<WurstSubscriber>> annotatedSubscribers =
		new ConcurrentHashMap<>();
	
	public EventManager(WurstClient wurst)
	{
		this.wurst = wurst;
	}
	
	/**
	 * Fires the given {@link Event} if Wurst is enabled and the
	 * {@link EventManager} is ready to accept events. This method is safe to
	 * call even when the EventManager hasn't been initialized yet.
	 */
	public static <L extends Listener, E extends Event<L>> void fire(E event)
	{
		EventManager eventManager = WurstClient.INSTANCE.getEventManager();
		if(eventManager == null)
			return;
		
		eventManager.fireImpl(event);
	}
	
	private <L extends Listener, E extends Event<L>> void fireImpl(E event)
	{
		if(!wurst.isEnabled())
			return;

		try
		{
			Class<L> type = event.getListenerType();
			@SuppressWarnings("unchecked")
			ArrayList<L> listeners =
				(ArrayList<L>)listenerSnapshots.get(type);

			if(listeners != null && !listeners.isEmpty())
			{
				event.fire(listeners);
				eventCounters.computeIfAbsent(type, ignored -> new LongAdder())
					.increment();
			}

			fireAnnotated(event);

			totalEventsFired.increment();

		}catch(Throwable e)
		{
			e.printStackTrace();

			CrashReport report = CrashReport.forThrowable(e, "Firing Wurst event");
			CrashReportCategory section = report.addCategory("Affected event");
			section.setDetail("Event class", () -> event.getClass().getName());

			throw new ReportedException(report);
		}
	}

	private void fireAnnotated(Event<?> event)
	{
		Class<?> eventClass = event.getClass();
		for(Map.Entry<Class<?>, List<WurstSubscriber>> entry : new ArrayList<>(
			annotatedSubscribers.entrySet()))
			if(entry.getKey().isAssignableFrom(eventClass))
				for(WurstSubscriber s : entry.getValue())
					s.callSubscriber(event);
	}
	
	public synchronized <L extends Listener> void add(Class<L> type, L listener)
	{
		try
		{
			@SuppressWarnings("unchecked")
			ArrayList<L> listeners = (ArrayList<L>)listenerMap.get(type);
			
			if(listeners == null)
			{
				listeners = new ArrayList<>();
				listenerMap.put(type, listeners);
			}
			
			listeners.add(listener);
			listenerSnapshots.put(type, new ArrayList<>(listeners));
			
		}catch(Throwable e)
		{
			e.printStackTrace();
			
			CrashReport report =
				CrashReport.forThrowable(e, "Adding Wurst event listener");
			CrashReportCategory section = report.addCategory("Affected listener");
			section.setDetail("Listener type", () -> type.getName());
			section.setDetail("Listener class", () -> listener.getClass().getName());
			
			throw new ReportedException(report);
		}
	}
	
	public synchronized <L extends Listener> void remove(Class<L> type,
		L listener)
	{
		try
		{
			@SuppressWarnings("unchecked")
			ArrayList<L> listeners = (ArrayList<L>)listenerMap.get(type);
			
			if(listeners == null)
				return;

			listeners.remove(listener);
			if(listeners.isEmpty())
				listenerSnapshots.remove(type);
			else
				listenerSnapshots.put(type, new ArrayList<>(listeners));
			
		}catch(Throwable e)
		{
			e.printStackTrace();
			
			CrashReport report =
				CrashReport.forThrowable(e, "Removing Wurst event listener");
			CrashReportCategory section = report.addCategory("Affected listener");
			section.setDetail("Listener type", () -> type.getName());
			section.setDetail("Listener class", () -> listener.getClass().getName());
			
			throw new ReportedException(report);
		}
	}

	public void subscribeAnnotated(Object obj)
	{
		for(Class<?> type = obj.getClass(); type != null && type != Object.class;
			type = type.getSuperclass())
		for(Method method : type.getDeclaredMethods())
		{
			if(!method.isAnnotationPresent(WurstSubscribe.class))
				continue;

			Class<?>[] params = method.getParameterTypes();
			if(params.length != 1
				|| !Event.class.isAssignableFrom(params[0]))
				continue;

			method.setAccessible(true);
			WurstSubscriber subscriber = new WurstSubscriber(obj, method);
			List<WurstSubscriber> subscribers = annotatedSubscribers
				.computeIfAbsent(subscriber.getEventClass(),
					k -> new CopyOnWriteArrayList<>());
			if(!subscribers.contains(subscriber))
				subscribers.add(subscriber);
		}
	}

	public void unsubscribeAnnotated(Object obj)
	{
		annotatedSubscribers.values().removeIf(v -> {
			boolean removed = v.removeIf(s -> s.isTarget(obj));
			return v.isEmpty();
		});
	}

	public long getEventCount(Class<? extends Listener> type)
	{
		LongAdder counter = eventCounters.get(type);
		return counter == null ? 0 : counter.sum();
	}

	public long getTotalEventsFired()
	{
		return totalEventsFired.sum();
	}

	public int getListenerCount(Class<? extends Listener> type)
	{
		ArrayList<? extends Listener> listeners = listenerSnapshots.get(type);
		return listeners == null ? 0 : listeners.size();
	}

	public int getTotalListenerCount()
	{
		int total = 0;
		for(ArrayList<? extends Listener> listeners : listenerSnapshots.values())
			total += listeners.size();
		return total;
	}

	public int getAnnotatedSubscriberCount(Class<? extends Event<?>> eventType)
	{
		List<WurstSubscriber> subscribers = annotatedSubscribers.get(eventType);
		return subscribers == null ? 0 : subscribers.size();
	}
}
