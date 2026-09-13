/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.inventory;

/**
 * 背包动作链的执行判定：该执行、该再等等、还是该放弃。
 *
 * <p>
 * 背景：{@link InventoryActionQueue} 原来是「先移除链、再校验」，于是
 * validator 一旦没通过（例如容器还在同步）或菜单 id 变了，这条动作就被**静默丢弃**，
 * 不会重试，owner 也拿不到回调。参考项目 CakeSlayers/OpenEpsilon 的背包队列用
 * 「事务确认 + 超时」补的正是这一半（其 {@code util/inventory/Task.kt}）。
 *
 * <p>
 * 这里把策略抽成纯函数，是为了能单测——三态判定最容易写错的地方是「等多久算超时」
 * 和「菜单换了要不要救」。真实执行仍然在 {@code InventoryActionQueue} 里，本类不碰
 * Minecraft。
 */
public final class ActionRetryPolicy
{
	/**
	 * 校验没通过时的等待窗口。容器同步通常在一两 tick 内完成，给 500ms 足够宽，
	 * 又不至于让一条永远不成立的动作赖在队列里。
	 */
	public static final long DEFAULT_RETRY_WINDOW_MS = 500L;
	
	public enum Decision
	{
		/** 条件齐了，可以执行。 */
		EXECUTE,
		/** 条件暂时不满足，留在队列里下一 tick 再看。 */
		RETRY,
		/** 没有救的意义，丢掉。 */
		ABORT
	}
	
	private ActionRetryPolicy()
	{}
	
	/**
	 * @param menuMatches     当前容器菜单是否还是提交时那个
	 * @param validatorPassed 调用方给的前置条件是否成立
	 * @param waitedMs        自提交以来经过的毫秒
	 * @param windowMs        允许等待的窗口
	 */
	public static Decision decide(boolean menuMatches,
		boolean validatorPassed, long waitedMs, long windowMs)
	{
		if(menuMatches && validatorPassed)
			return Decision.EXECUTE;
		
		/*
		 * 菜单换了就放弃：动作是针对某个容器编排的，套到另一个容器上会点错格子，
		 * 这跟"再等等"是两回事，不能靠重试救回来。
		 */
		if(!menuMatches)
			return Decision.ABORT;
		
		// 校验没过：窗口内再等，超时放弃，避免永久占着队列
		return waitedMs >= Math.max(0L, windowMs) ? Decision.ABORT
			: Decision.RETRY;
	}
	
	/** 判定一条还没执行的链是不是"已经等太久了"。 */
	public static boolean isExpired(long submittedAtMs, long nowMs,
		long windowMs)
	{
		return nowMs - submittedAtMs >= Math.max(0L, windowMs);
	}
}
