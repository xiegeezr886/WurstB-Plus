/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.wurstclient.WurstClient;

final class EventManagerPriorityTest
{
	@Test
	void higherPriorityIsCalledFirstAndTiesKeepInsertionOrder()
	{
		EventManager manager = new EventManager(WurstClient.INSTANCE);
		TestListener default1 = () -> {};
		TestListener lowPriority = () -> {};
		TestListener highPriority = () -> {};
		TestListener mediumFirst = () -> {};
		TestListener mediumSecond = () -> {};

		manager.add(TestListener.class, default1);
		manager.add(TestListener.class, lowPriority, -5);
		manager.add(TestListener.class, mediumFirst, 5);
		manager.add(TestListener.class, highPriority, 100);
		manager.add(TestListener.class, mediumSecond, 5);

		assertEquals(List.of(highPriority, mediumFirst, mediumSecond,
			default1, lowPriority), manager.getListeners(TestListener.class));
	}

	@Test
	void defaultAddBehavesLikePriorityZero()
	{
		EventManager manager = new EventManager(WurstClient.INSTANCE);
		TestListener first = () -> {};
		TestListener second = () -> {};

		manager.add(TestListener.class, first);
		manager.add(TestListener.class, second);

		// 同优先级 -> 注册先后顺序不变（旧行为）
		assertEquals(List.of(first, second), manager.getListeners(TestListener.class));
	}

	@Test
	void removalKeepsPrioritiesAligned()
	{
		EventManager manager = new EventManager(WurstClient.INSTANCE);
		TestListener first = () -> {};
		TestListener middle = () -> {};
		TestListener last = () -> {};

		manager.add(TestListener.class, first, 10);
		manager.add(TestListener.class, middle, 5);
		manager.add(TestListener.class, last, 1);
		assertEquals(List.of(first, middle, last),
			manager.getListeners(TestListener.class));

		manager.remove(TestListener.class, middle);
		assertEquals(List.of(first, last), manager.getListeners(TestListener.class));

		// 删掉中间那个之后，后面再插入的优先级仍然排对位置
		TestListener between = () -> {};
		manager.add(TestListener.class, between, 7);
		assertEquals(List.of(first, between, last),
			manager.getListeners(TestListener.class));
	}

	@Test
	void removingEverythingClearsTheType()
	{
		EventManager manager = new EventManager(WurstClient.INSTANCE);
		TestListener listener = () -> {};
		manager.add(TestListener.class, listener, 3);

		manager.remove(TestListener.class, listener);
		assertEquals(0, manager.getListenerCount(TestListener.class));
		assertTrue(manager.getListeners(TestListener.class).isEmpty());
	}

	/**
	 * 回归测试：全部移除之后**再加回来**曾经抛 NPE。
	 *
	 * <p>原因：{@code remove} 在列表变空时清掉了 priorityMap 与
	 * listenerSnapshots，却漏掉 listenerMap，于是那里留着一个空列表；下次
	 * {@code add} 取到的 listeners 非空、priorities 却是 null，紧接着
	 * {@code priorities.size()} 就崩。
	 *
	 * <p>实机复现路径：ClickGUI 里开 MultiAura → 关掉 → 再开。上面的
	 * {@link #removingEverythingClearsTheType()} 只检查了"移除后为空"，没检查
	 * "再加回来"，所以当初没拦住这个 bug。
	 */
	@Test
	void readdingAfterRemovingEverythingWorks()
	{
		EventManager manager = new EventManager(WurstClient.INSTANCE);
		TestListener listener = () -> {};
		manager.add(TestListener.class, listener, 3);

		manager.remove(TestListener.class, listener);
		manager.add(TestListener.class, listener, 3);
		assertEquals(List.of(listener), manager.getListeners(TestListener.class));

		// 反复开关也要稳（对应 hack 反复启用/禁用）
		for(int i = 0; i < 3; i++)
		{
			manager.remove(TestListener.class, listener);
			manager.add(TestListener.class, listener, i);
		}
		assertEquals(List.of(listener), manager.getListeners(TestListener.class));
	}

	private interface TestListener extends Listener
	{
		void onTest();
	}
}
