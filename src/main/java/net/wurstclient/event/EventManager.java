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
	/** 与 listenerMap 里每个列表一一对应的优先级，用于按优先级插入。 */
	private final HashMap<Class<? extends Listener>, ArrayList<Integer>> priorityMap =
		new HashMap<>();
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
	
	/**
	 * 以默认优先级 0 注册监听器，等价于 {@code add(type, listener, 0)}。
	 */
	public synchronized <L extends Listener> void add(Class<L> type, L listener)
	{
		add(type, listener, 0);
	}

	/**
	 * 按优先级注册监听器：**数值大的先被调用**，同优先级保持注册先后顺序。
	 *
	 * <p>为什么需要它：注册顺序原本只取决于谁先在代码里 {@code add}，而同一个事件上不同
	 * 监听器之间是有先后要求的（例如 {@link net.wurstclient.RotationFaker} 必须在每 tick 的
	 * PostMotion 阶段**最先**清空上一 tick 的朝向，否则同 tick 里后设的朝向会被它抹掉）。
	 * 有了优先级，这种要求由显式数字表达，而不是靠"谁先被 new 出来"的隐含顺序。
	 */
	public synchronized <L extends Listener> void add(Class<L> type, L listener,
		int priority)
	{
		try
		{
			@SuppressWarnings("unchecked")
			ArrayList<L> listeners = (ArrayList<L>)listenerMap.get(type);
			ArrayList<Integer> priorities = priorityMap.get(type);

			// 两个 map 必须各判各的。只判 listeners 会在两者不同步时把
			// priorities 留成 null，紧接着 priorities.size() 抛 NPE。
			if(listeners == null)
			{
				listeners = new ArrayList<>();
				listenerMap.put(type, listeners);
			}
			if(priorities == null)
			{
				priorities = new ArrayList<>();
				priorityMap.put(type, priorities);
			}

			int index = listeners.size();
			for(int i = 0; i < priorities.size(); i++)
				if(priorities.get(i) < priority)
				{
					index = i;
					break;
				}

			listeners.add(index, listener);
			priorities.add(index, priority);
			listenerSnapshots.put(type, new ArrayList<>(listeners));

		}catch(Throwable e)
		{
			e.printStackTrace();
			
			CrashReport report =
				CrashReport.forThrowable(e, "Adding Wurst event listener");
			CrashReportCategory section = report.addCategory("Affected listener");
			section.setDetail("Listener type", () -> type.getName());
			section.setDetail("Listener class", () -> listener.getClass().getName());
			section.setDetail("Priority", () -> Integer.toString(priority));
			
			throw new ReportedException(report);
		}
	}

	/**
	 * 当前已注册监听器的只读快照（按调用顺序）。用于调试与测试。
	 */
	public <L extends Listener> List<L> getListeners(Class<L> type)
	{
		@SuppressWarnings("unchecked")
		ArrayList<L> snapshot = (ArrayList<L>)listenerSnapshots.get(type);
		return snapshot == null ? List.of() : List.copyOf(snapshot);
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

			int index = listeners.indexOf(listener);
			if(index < 0)
				return;

			listeners.remove(index);
			ArrayList<Integer> priorities = priorityMap.get(type);
			if(priorities != null && index < priorities.size())
				priorities.remove(index);

			if(listeners.isEmpty())
			{
				// 三个 map 必须一起去掉。漏掉 listenerMap 会让它留下一个空列表，
				// 下次 add 时 listeners != null 但 priorityMap 里已无条目，
				// priorities 取到 null -> 紧跟其后的 priorities.size() 抛 NPE。
				// 实测复现：开 MultiAura -> 关 -> 再开即崩。
				listenerMap.remove(type);
				listenerSnapshots.remove(type);
				priorityMap.remove(type);
			}else
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
				insertByPriority(subscribers, subscriber);
		}
	}

	/**
	 * 按优先级从高到低插入；同优先级放在已有同优先级订阅者**之后**，
	 * 所以注册顺序仍然保留（稳定）。原来只是 {@code add()}，注解订阅无法表达调用顺序。
	 */
	private static void insertByPriority(List<WurstSubscriber> subscribers,
		WurstSubscriber subscriber)
	{
		int priority = subscriber.getPriority();
		int index = 0;
		while(index < subscribers.size()
			&& subscribers.get(index).getPriority() >= priority)
			index++;

		subscribers.add(index, subscriber);
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

	/**
	 * 只读快照：某个事件类的注解订阅者，**顺序就是调用顺序**（已按优先级从高到低排好）。
	 */
	public List<WurstSubscriber> getAnnotatedSubscribers(
		Class<? extends Event<?>> eventType)
	{
		List<WurstSubscriber> subscribers = annotatedSubscribers.get(eventType);
		return subscribers == null ? List.of() : List.copyOf(subscribers);
	}
}
