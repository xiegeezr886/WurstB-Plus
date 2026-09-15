/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.util.inventory.ActionRetryPolicy.Decision;

/**
 * 锁住背包动作链的三态判定。写错的方向有两个且都不会编译报错：把"菜单换了"也当成
 * 可重试（会点错格子），或者窗口判断写反导致永不超时（队列被一条死链占住）。
 */
public final class ActionRetryPolicyTest
{
	private static final long WINDOW = 500L;
	
	@Test
	public void executesWhenEverythingLinesUp()
	{
		assertEquals(Decision.EXECUTE,
			ActionRetryPolicy.decide(true, true, 0L, WINDOW));
		assertEquals(Decision.EXECUTE,
			ActionRetryPolicy.decide(true, true, 10_000L, WINDOW));
	}
	
	@Test
	public void retriesWhileTheValidatorIsStillFailing()
	{
		assertEquals(Decision.RETRY,
			ActionRetryPolicy.decide(true, false, 0L, WINDOW));
		assertEquals(Decision.RETRY,
			ActionRetryPolicy.decide(true, false, WINDOW - 1, WINDOW));
	}
	
	@Test
	public void abortsOnceTheWindowIsUp()
	{
		assertEquals(Decision.ABORT,
			ActionRetryPolicy.decide(true, false, WINDOW, WINDOW));
		assertEquals(Decision.ABORT,
			ActionRetryPolicy.decide(true, false, WINDOW * 10, WINDOW));
	}
	
	@Test
	public void aChangedMenuIsNeverRetried()
	{
		// 菜单换了：无论等多久、校验过不过，都不能套到新容器上执行
		assertEquals(Decision.ABORT,
			ActionRetryPolicy.decide(false, true, 0L, WINDOW));
		assertEquals(Decision.ABORT,
			ActionRetryPolicy.decide(false, false, 0L, WINDOW));
	}
	
	@Test
	public void aZeroWindowMeansNoRetryAtAll()
	{
		// 窗口为 0 时不应该出现"永远再等一 tick"
		assertEquals(Decision.ABORT,
			ActionRetryPolicy.decide(true, false, 0L, 0L));
	}
	
	@Test
	public void negativeWindowsAreTreatedAsZero()
	{
		assertEquals(Decision.ABORT,
			ActionRetryPolicy.decide(true, false, 0L, -100L));
	}
	
	@Test
	public void expiryMatchesTheWindowBoundary()
	{
		assertFalse(ActionRetryPolicy.isExpired(1_000L, 1_000L + WINDOW - 1,
			WINDOW));
		// 边界上就算过期，与 decide 的 >= 保持一致
		assertTrue(ActionRetryPolicy.isExpired(1_000L, 1_000L + WINDOW,
			WINDOW));
		assertTrue(ActionRetryPolicy.isExpired(1_000L, 1_000L + WINDOW * 3,
			WINDOW));
	}
	
	@Test
	public void decideAndExpiryAgree()
	{
		long submittedAt = 5_000L;
		
		for(long waited = 0L; waited <= WINDOW * 2; waited += 25L)
		{
			Decision decision =
				ActionRetryPolicy.decide(true, false, waited, WINDOW);
			boolean expired = ActionRetryPolicy.isExpired(submittedAt,
				submittedAt + waited, WINDOW);
			
			// 两者必须给出同一个结论，否则接线时会用到互相矛盾的两套判断
			assertEquals(expired, decision == Decision.ABORT,
				"waited=" + waited + " 时两套判断不一致");
		}
	}
	
	/**
	 * 窗口的语义是"留多少个服务端 tick"，所以低 TPS 必须把它拉长。写反了不会编译
	 * 报错，但会让服务端一卡就再也等不到容器同步。
	 */
	@Test
	public void stretchesTheWindowWhenTheServerIsSlow()
	{
		assertEquals(500L, ActionRetryPolicy.compensatedWindow(500L, 20.0D));
		assertEquals(1_000L, ActionRetryPolicy.compensatedWindow(500L, 10.0D));
		assertEquals(2_000L, ActionRetryPolicy.compensatedWindow(500L, 5.0D));
	}
	
	/** 服务端超频（> 20 TPS）反而应该缩短窗口，与参考的 20/tickRate 一致。 */
	@Test
	public void shortensTheWindowWhenTheServerIsFast()
	{
		assertEquals(400L, ActionRetryPolicy.compensatedWindow(500L, 25.0D));
	}
	
	/** TPS 还没测出来时不做补偿：宁可保持原样，也不要拿 0 或 NaN 去缩放。 */
	@Test
	public void leavesTheWindowAloneWhenTpsIsNotMeasured()
	{
		assertEquals(500L, ActionRetryPolicy.compensatedWindow(500L, 0.0D));
		assertEquals(500L, ActionRetryPolicy.compensatedWindow(500L,
			Double.NaN));
		assertEquals(500L, ActionRetryPolicy.compensatedWindow(500L,
			Double.POSITIVE_INFINITY));
	}
	
	/** 服务端卡到极低 TPS 时窗口会线性膨胀，必须封顶，否则一条链能占住队列好几秒。 */
	@Test
	public void capsTheStretchedWindow()
	{
		assertEquals(ActionRetryPolicy.MAX_RETRY_WINDOW_MS,
			ActionRetryPolicy.compensatedWindow(500L, 1.0D));
		assertEquals(ActionRetryPolicy.MAX_RETRY_WINDOW_MS,
			ActionRetryPolicy.compensatedWindow(60_000L, 20.0D));
	}
	
	/** 窗口不能是负数，否则 decide() 会把所有链立刻判超时。 */
	@Test
	public void neverReturnsANegativeWindow()
	{
		assertEquals(0L, ActionRetryPolicy.compensatedWindow(-500L, 20.0D));
		assertEquals(0L, ActionRetryPolicy.compensatedWindow(-500L, 10.0D));
	}
	
	/** 补偿必须是单调的：TPS 越低，窗口不得更短。 */
	@Test
	public void isMonotonicInTps()
	{
		long previous = -1L;
		
		for(double tps = 20.0D; tps >= 1.0D; tps -= 0.5D)
		{
			long window = ActionRetryPolicy.compensatedWindow(500L, tps);
			
			assertTrue(window >= previous,
				"tps=" + tps + " 时窗口比更高 TPS 时更短：" + window);
			previous = window;
		}
	}
}
