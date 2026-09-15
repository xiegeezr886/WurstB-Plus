/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import net.wurstclient.WurstClient;

/**
 * 注解订阅的调用顺序：{@code @WurstSubscribe(priority = N)} 数值大的先跑，
 * 同优先级按注册顺序（稳定）。
 *
 * <p>
 * 这里断言的是 {@code getAnnotatedSubscribers()} 返回的列表顺序 —— 那正是
 * {@code fireAnnotated()} 里 {@code for(WurstSubscriber s : entry.getValue())} 的迭代顺序。
 * 之所以不直接 {@code fire()} 一个事件来观察调用：{@code EventManager.fireImpl()} 开头有
 * {@code if(!wurst.isEnabled()) return;}，离线单测里客户端没有启用，事件根本不会派发，
 * 断言就会变成"永远为空"的假通过。
 */
public class EventManagerAnnotatedPriorityTest
{
	@Test
	public void higherPriorityRunsFirst()
	{
		EventManager manager = new EventManager(WurstClient.INSTANCE);

		// 故意乱序注册
		manager.subscribeAnnotated(new LowPriority());
		manager.subscribeAnnotated(new HighPriority());
		manager.subscribeAnnotated(new MiddlePriority());

		assertEquals(List.of("HighPriority", "MiddlePriority", "LowPriority"),
			targetsOf(manager));
		assertEquals(List.of(10, 0, -5), prioritiesOf(manager));
	}

	@Test
	public void samePriorityKeepsRegistrationOrder()
	{
		EventManager manager = new EventManager(WurstClient.INSTANCE);

		manager.subscribeAnnotated(new MiddlePriority());
		manager.subscribeAnnotated(new AlsoMiddlePriority());

		assertEquals(List.of("MiddlePriority", "AlsoMiddlePriority"),
			targetsOf(manager));
	}

	@Test
	public void defaultPriorityIsZero()
	{
		EventManager manager = new EventManager(WurstClient.INSTANCE);

		manager.subscribeAnnotated(new LowPriority());
		manager.subscribeAnnotated(new DefaultPriority());

		// 默认 0 高于 -5，所以 DefaultPriority 排在前面
		assertEquals(List.of("DefaultPriority", "LowPriority"),
			targetsOf(manager));
		assertEquals(List.of(0, -5), prioritiesOf(manager));
	}

	@Test
	public void unsubscribingRemovesAnnotatedSubscriber()
	{
		EventManager manager = new EventManager(WurstClient.INSTANCE);
		HighPriority high = new HighPriority();

		manager.subscribeAnnotated(high);
		assertEquals(1, manager.getAnnotatedSubscriberCount(DummyEvent.class));

		manager.unsubscribeAnnotated(high);
		assertEquals(0, manager.getAnnotatedSubscriberCount(DummyEvent.class));
	}

	private static List<String> targetsOf(EventManager manager)
	{
		return manager.getAnnotatedSubscribers(DummyEvent.class).stream()
			.map(WurstSubscriber::getTargetClass).map(Class::getSimpleName)
			.toList();
	}

	private static List<Integer> prioritiesOf(EventManager manager)
	{
		return manager.getAnnotatedSubscribers(DummyEvent.class).stream()
			.map(WurstSubscriber::getPriority).toList();
	}

	private interface DummyListener extends Listener
	{
		void onDummy(DummyEvent event);
	}

	private static final class DummyEvent extends Event<DummyListener>
	{
		@Override
		public void fire(ArrayList<DummyListener> listeners)
		{
			for(DummyListener listener : listeners)
				listener.onDummy(this);
		}

		@Override
		public Class<DummyListener> getListenerType()
		{
			return DummyListener.class;
		}
	}

	private static final class HighPriority
	{
		@WurstSubscribe(priority = 10)
		public void onDummy(DummyEvent event)
		{
		}
	}

	private static final class MiddlePriority
	{
		@WurstSubscribe(priority = 0)
		public void onDummy(DummyEvent event)
		{
		}
	}

	private static final class AlsoMiddlePriority
	{
		@WurstSubscribe(priority = 0)
		public void onDummy(DummyEvent event)
		{
		}
	}

	private static final class DefaultPriority
	{
		@WurstSubscribe
		public void onDummy(DummyEvent event)
		{
		}
	}

	private static final class LowPriority
	{
		@WurstSubscribe(priority = -5)
		public void onDummy(DummyEvent event)
		{
		}
	}
}
