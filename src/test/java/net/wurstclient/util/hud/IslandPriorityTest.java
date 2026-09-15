/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import net.wurstclient.util.hud.IslandPriority.Entry;

final class IslandPriorityTest
{
	@Test
	void theDefaultPriorityIsZero()
	{
		assertEquals(0, IslandPriority.DEFAULT_PRIORITY);

		IslandPriority<String> stack = new IslandPriority<>();
		stack.add("default");

		assertEquals(0, stack.head().priority());
	}

	@Test
	void higherPriorityWinsRegardlessOfInsertionOrder()
	{
		IslandPriority<String> lowFirst = new IslandPriority<>();
		lowFirst.add("music", 5);
		lowFirst.add("notification", 50);
		assertEquals("notification", lowFirst.decidingTrigger());

		IslandPriority<String> highFirst = new IslandPriority<>();
		highFirst.add("notification", 50);
		highFirst.add("music", 5);
		assertEquals("notification", highFirst.decidingTrigger());
	}

	/**
	 * 参考用 {@code Collections.sort}（稳定排序），所以同优先级下先加入的排在
	 * 前面、也就先胜出。参考没把这条写进契约，但结果可复算；这里当成显式契约
	 * 钉住，免得以后有人把「后加入者顶掉」当成修复。
	 */
	@Test
	void tiesResolveToTheEarliestInserted()
	{
		IslandPriority<String> stack = new IslandPriority<>();
		stack.add("first", 10);
		stack.add("second", 10);
		stack.add("third", 10);

		assertEquals("first", stack.decidingTrigger());
		assertEquals("first", stack.head().trigger());
	}

	@Test
	void aLaterLowerPriorityTriggerDoesNotShadowTheHead()
	{
		IslandPriority<String> stack = new IslandPriority<>();
		stack.add("winner", 10);
		stack.add("loser", 9);

		assertEquals("winner", stack.decidingTrigger());
	}

	@Test
	void negativePrioritiesSortBelowTheDefault()
	{
		IslandPriority<String> stack = new IslandPriority<>();
		stack.add("default");
		stack.add("lower", -5);

		assertEquals("default", stack.decidingTrigger());
	}

	@Test
	void addingTheSameTriggerTwiceDoesNotDuplicateIt()
	{
		IslandPriority<String> stack = new IslandPriority<>();

		assertTrue(stack.add("music", 5));
		assertFalse(stack.add("music", 99));
		assertEquals(1, stack.size());
		// 第二次的优先级不该顶掉第一次的
		assertEquals(5, stack.head().priority());
	}

	@Test
	void removingTheHeadFallsBackToTheNextHighest()
	{
		IslandPriority<String> stack = new IslandPriority<>();
		stack.add("music", 5);
		stack.add("notification", 50);

		assertTrue(stack.remove("notification"));
		assertEquals("music", stack.decidingTrigger());
		assertFalse(stack.remove("notification"));
	}

	/**
	 * 参考的 {@code isActive()}：表头是自带坐标的触发者时，元素本体完全不画，
	 * 绘制让给那个触发者自己的模块。
	 */
	@Test
	void aSelfPositionedHeadSuppressesTheElement()
	{
		IslandPriority<String> stack = new IslandPriority<>();
		stack.add("default");
		stack.addSelfPositioned("custom-hud", 100);

		assertEquals("custom-hud", stack.decidingTrigger());
		assertTrue(stack.head().selfPositioned());
		assertFalse(stack.elementShouldDraw());
	}

	@Test
	void aSelfPositionedTriggerThatIsNotTheHeadDoesNotSuppressAnything()
	{
		IslandPriority<String> stack = new IslandPriority<>();
		stack.addSelfPositioned("custom-hud", 1);
		stack.add("notification", 50);

		assertEquals("notification", stack.decidingTrigger());
		assertTrue(stack.elementShouldDraw());
	}

	@Test
	void anEmptyStackHasNoHeadAndDrawsNothing()
	{
		IslandPriority<String> stack = new IslandPriority<>();

		assertTrue(stack.isEmpty());
		assertNull(stack.head());
		assertNull(stack.decidingTrigger());
		assertFalse(stack.elementShouldDraw());
	}

	/**
	 * 清空后再加，仍然按新加入顺序决定并列胜者（不残留旧的顺序）。
	 */
	@Test
	void clearResetsTheInsertionOrder()
	{
		IslandPriority<String> stack = new IslandPriority<>();
		stack.add("old", 10);
		stack.clear();

		stack.add("new", 10);
		assertEquals("new", stack.decidingTrigger());
	}

	@Test
	void sortedIsDescendingAndStable()
	{
		IslandPriority<String> stack = new IslandPriority<>();
		stack.add("c", 1);
		stack.add("a", 9);
		stack.add("b", 9);
		stack.add("d", 5);

		assertEquals(List.of("a", "b", "d", "c"),
			stack.sorted().stream().map(Entry::trigger)
				.collect(Collectors.toList()));
	}

	@Test
	void sortedReturnsAnUnmodifiableSnapshot()
	{
		IslandPriority<String> stack = new IslandPriority<>();
		stack.add("music", 5);

		List<Entry<String>> snapshot = stack.sorted();
		stack.add("notification", 50);

		try
		{
			snapshot.add(new Entry<>("x", 0, false));
			throw new AssertionError("expected an UnsupportedOperationException");
		}catch(UnsupportedOperationException expected)
		{
			// 预期
		}

		// 快照是当时的状态，不是活视图
		assertEquals(1, snapshot.size());
		assertEquals(2, stack.size());
	}

	@Test
	void entriesRejectANullTrigger()
	{
		try
		{
			new Entry<String>(null, 0, false);
			throw new AssertionError("expected a NullPointerException");
		}catch(NullPointerException expected)
		{
			// 预期
		}
	}
}
