/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.inventory;

import net.wurstclient.util.TpsCompensation;

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
	
	/**
	 * 补偿后的等待窗口上限。
	 *
	 * <p>
	 * 换算是线性的：服务端掉到 1 TPS 时 500ms 会被拉成 10 秒。窗口本身是为了等容器
	 * 同步，而服务端真卡到那个地步时，与其让一条链把队列占满十秒，不如按上限放弃、
	 * 让 owner 走别的路。所以这里封顶。
	 */
	public static final long MAX_RETRY_WINDOW_MS = 5_000L;
	
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
	
	/**
	 * 把"按 20 TPS 写的等待窗口"换算成当前 tick 速率下应该等的毫秒。
	 *
	 * <p>
	 * 窗口的含义是"给容器同步留多少服务端 tick"，不是"留多少墙钟时间"。服务端掉到
	 * 10 TPS 时，同样的 500 毫秒只等于 5 个 tick，容器可能还没同步完链就被判超时——
	 * 这正是参考项目统一按 {@code 20 / tickRate} 缩放的那件事（见
	 * {@link TpsCompensation}）。TPS 还没测出来时按原值处理，不做补偿。
	 *
	 * <p>
	 * 纯函数，便于单测；结果被夹在 {@code [0, }{@link #MAX_RETRY_WINDOW_MS}{@code ]}。
	 */
	public static long compensatedWindow(long baseWindowMs, double tps)
	{
		long base = Math.max(0L, baseWindowMs);
		
		if(!TpsCompensation.isMeasured(tps))
			return base;
		
		long scaled = Math.round(TpsCompensation.scaleMillis(base, tps));
		return Math.max(0L, Math.min(MAX_RETRY_WINDOW_MS, scaled));
	}
}
