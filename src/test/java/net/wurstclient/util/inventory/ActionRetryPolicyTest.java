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
}
