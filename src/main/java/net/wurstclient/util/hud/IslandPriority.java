/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 「优先级栈」纯逻辑，移植自 OpenOpal 的
 * {@code DynamicIslandElement} + {@code IslandTrigger}（GPL-3.0）。
 *
 * <p>
 * 参考的模型是：一堆触发者按优先级<b>降序</b>排，永远只有<b>表头</b>能画
 * （{@code IslandTrigger.compareTo} 是
 * {@code Integer.compare(o.priority, priority)}，
 * {@code getDecidingTrigger()} 就是 {@code ACTIVE_TRIGGERS.getFirst()}）。
 * 也就是说「优先级最高者独占」，而不是叠加显示。
 *
 * <p>
 * 本类只搬这层挑选逻辑，不含渲染，也不含任何 Minecraft 依赖，所以能单测；
 * 触发者本身是什么类型由泛型决定。
 *
 * <h2>目前没有任何调用者，而且这是有意为之</h2>
 * <p>
 * 写完之后去接的第一个候选是 {@code MusicIslandHudElement}（751 行）。核对后
 * 结论是<b>不该接</b>：那个岛今天的「竞争」只有一个布尔量——
 * {@code boolean musicVisible = playing || loading}（第 185 行），内容由
 * {@code compactText()} 三选一（放歌名 / 加载中 / {@code mc.getFps() + " FPS"}
 * 兜底）。也就是<b>一个主状态 + 一个严格兜底</b>；而这层栈的价值完全来自
 * 「多个互斥主张者抢表头」——两个主张者、且其中一个是另一个的兜底时，
 * 栈比那个布尔量更啰嗦，一个字节的行为都改变不了。
 * <p>
 * 要让它产生价值就得<b>发明</b>新的主张者（低血量警告、通知接管岛面……），
 * 每一个都是新的用户可见功能、需要先定 UX，那属于设计新功能，不属于「接线」；
 * 而且要在无法进游戏验证的前提下动那个 751 行的渲染类。所以这里停手。
 * <p>
 * 保留本类而不是删掉，是因为它是一次忠实的移植、有 14 个单测；但读者不要
 * 误以为它是活的——<b>它现在是死代码</b>。若确认不再需要第三个主张者，
 * 删掉它是更干净的选择。
 *
 * <h2>与参考的两处有意分歧</h2>
 * <ol>
 * <li><b>不缓存排序结果。</b>参考为了省掉每帧排序，用一个静态
 * {@code SORTING_DIRTY} 标志做惰性排序，而那个标志是<b>非 volatile 的静态字段</b>，
 * 由模块开关回调写、由渲染线程读，属于跨线程可见性问题（研究报告 §3-D3）。
 * 这里改成按需扫描取表头（栈里通常只有个位数元素，O(n) 比排序还便宜），
 * 于是既不需要缓存，也不存在那个字段。</li>
 * <li><b>并列时不看插入顺序以外的东西。</b>参考用 {@code Collections.sort}，
 * 那是稳定排序，所以同优先级下<b>先加入的排在前面</b>、先加入者胜出。
 * 这一点参考没有写明，但可复算；本类把同一行为写成了显式契约并加了测试。</li>
 * </ol>
 *
 * @param <T>
 *            触发者类型。
 */
public final class IslandPriority<T>
{
	/** 参考 {@code IslandTrigger.getIslandPriority()} 的默认值。 */
	public static final int DEFAULT_PRIORITY = 0;

	private final List<Entry<T>> entries = new ArrayList<>();

	/**
	 * 栈里的一项。
	 *
	 * @param trigger
	 *            触发者本体。
	 * @param priority
	 *            数值越大越优先。
	 * @param selfPositioned
	 *            对应参考的 {@code CustomIslandTrigger}：自带坐标、不走对齐
	 *            设置，并且表头是它时元素本体完全不画。
	 */
	public record Entry<T>(T trigger, int priority, boolean selfPositioned)
	{
		public Entry
		{
			Objects.requireNonNull(trigger, "trigger");
		}
	}

	public boolean add(T trigger)
	{
		return add(trigger, DEFAULT_PRIORITY);
	}

	public boolean add(T trigger, int priority)
	{
		return addEntry(new Entry<>(trigger, priority, false));
	}

	public boolean addSelfPositioned(T trigger, int priority)
	{
		return addEntry(new Entry<>(trigger, priority, true));
	}

	private boolean addEntry(Entry<T> entry)
	{
		if(contains(entry.trigger()))
			return false;
		entries.add(entry);
		return true;
	}

	public boolean remove(T trigger)
	{
		return entries.removeIf(entry -> entry.trigger().equals(trigger));
	}

	public boolean contains(T trigger)
	{
		return entries.stream()
			.anyMatch(entry -> entry.trigger().equals(trigger));
	}

	public int size()
	{
		return entries.size();
	}

	public boolean isEmpty()
	{
		return entries.isEmpty();
	}

	public void clear()
	{
		entries.clear();
	}

	/**
	 * 表头，也就是优先级最高的一项；并列时取最先加入的。空栈返回
	 * {@code null}。
	 *
	 * <p>
	 * 这里用严格大于比较，所以并列时不会被后来的顶掉——与参考那个稳定排序
	 * 的结果一致。
	 */
	public Entry<T> head()
	{
		Entry<T> best = null;
		for(Entry<T> entry : entries)
			if(best == null || entry.priority() > best.priority())
				best = entry;
		return best;
	}

	/** 表头的触发者本体，空栈返回 {@code null}。 */
	public T decidingTrigger()
	{
		Entry<T> head = head();
		return head == null ? null : head.trigger();
	}

	/**
	 * 元素本体该不该画。参考的 {@code isActive()} 在表头是自带坐标的触发者时
	 * 返回 {@code false}，把绘制完全让给那个触发者自己的模块。空栈同样返回
	 * {@code false}（没有触发者就没有内容可画）。
	 */
	public boolean elementShouldDraw()
	{
		Entry<T> head = head();
		return head != null && !head.selfPositioned();
	}

	/**
	 * 降序快照；并列时保持加入顺序。返回的列表不可修改。
	 */
	public List<Entry<T>> sorted()
	{
		List<Entry<T>> copy = new ArrayList<>(entries);
		// List.sort 是稳定排序，所以并列项保持加入顺序
		copy.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
		return Collections.unmodifiableList(copy);
	}
}
