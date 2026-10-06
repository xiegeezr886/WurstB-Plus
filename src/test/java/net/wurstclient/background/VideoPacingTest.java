/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.wurstclient.background.VideoPacing.Action;

/**
 * 视频背景的时间规则。
 *
 * <p>
 * 这些判定直接决定「画面停在哪一帧」，而且只有跑起来才看得见效果，所以在单测里把
 * 边界钉死：到点的那一毫秒算谁、60fps 的源在 30fps 上限下丢哪一半、落后多少才算
 * 脱节、循环怎么取模。真实解码不在这里验（那需要解码器），这里只验算术。
 * </p>
 */
final class VideoPacingTest
{
	private static final int INTERVAL =
		VideoPacing.minFrameIntervalMs(VideoPacing.CAP_FPS);

	@Test
	void theFrameRateCapIsAtMostThirtyFps()
	{
		assertEquals(33, INTERVAL);
		assertEquals(30, VideoPacing.CAP_FPS);

		assertEquals(1000, VideoPacing.minFrameIntervalMs(1));

		// 上限写成 0 或者负数按「不限帧」算，而不是除出 0 来把画面冻住，
		// 也不要变成 1fps 的幻灯片
		assertEquals(1, VideoPacing.minFrameIntervalMs(0));
		assertEquals(1, VideoPacing.minFrameIntervalMs(-30));
	}

	/** 还没显示过任何一帧时，第一帧必定显示，否则画面会一直空着。 */
	@Test
	void theFirstFrameIsAlwaysShown()
	{
		assertTrue(VideoPacing.shouldShowFrame(0, VideoPacing.NO_FRAME, INTERVAL));
		assertEquals(Action.SHOW, VideoPacing.decide(0, 0, VideoPacing.NO_FRAME,
			INTERVAL, 1500, 5_000));
	}

	/** 60fps 的源在 30fps 上限下隔一帧丢一帧，30fps 与 24fps 的源一帧都不丢。 */
	@Test
	void theCapDropsOnlyTheFramesItHasTo()
	{
		long lastShown = 0;

		// 60fps：16.7ms 一帧，只有隔一帧才够 33ms
		assertFalse(VideoPacing.shouldShowFrame(17, lastShown, INTERVAL));
		assertTrue(VideoPacing.shouldShowFrame(33, lastShown, INTERVAL));
		assertFalse(VideoPacing.shouldShowFrame(50, 33, INTERVAL));
		assertTrue(VideoPacing.shouldShowFrame(67, 33, INTERVAL));

		// 30fps：时间戳取整后落在 33 上，正好等于 1000/30 的整数商，不能因此被丢
		assertTrue(VideoPacing.shouldShowFrame(33, 0, INTERVAL));
		assertTrue(VideoPacing.shouldShowFrame(67, 33, INTERVAL));

		// 24fps：41.7ms 一帧，全都超过间隔
		assertEquals(Action.SHOW,
			VideoPacing.decide(83, 42, 0, INTERVAL, 1500, 5_000));
	}

	/** 同一毫秒上判两次仍然是「显示」，也就是不会因为相等而被丢。 */
	@Test
	void aFrameExactlyAtTheIntervalIsShown()
	{
		assertTrue(VideoPacing.shouldShowFrame(INTERVAL, 0, INTERVAL));
		assertFalse(VideoPacing.shouldShowFrame(INTERVAL - 2, 0, INTERVAL));
	}

	@Test
	void aFrameInTheFutureWaits()
	{
		assertEquals(Action.WAIT, VideoPacing.decide(100, 500,
			VideoPacing.NO_FRAME, INTERVAL, 1500, 5_000));
		assertEquals(Action.WAIT, VideoPacing.decide(100, 101,
			VideoPacing.NO_FRAME, INTERVAL, 1500, 5_000));

		// 到点的那一毫秒不再等
		assertEquals(Action.SHOW, VideoPacing.decide(100, 100,
			VideoPacing.NO_FRAME, INTERVAL, 1500, 5_000));
	}

	/**
	 * 远在未来的帧只等一个有上限的时间。
	 *
	 * <p>
	 * 实测的两种真实情况要靠这个上限区分：壁纸里长达数秒的静止段（PTS 上就是一个
	 * 大空档，等下去是对的）和坏掉的时间戳（跳到几小时后，照它睡画面就永久冻住）。
	 * </p>
	 */
	@Test
	void aFrameTooFarInTheFutureIsNotWaitedFor()
	{
		assertEquals(Action.WAIT, VideoPacing.decide(0, 5_000,
			VideoPacing.NO_FRAME, INTERVAL, 1500, 5_000));
		assertEquals(Action.RESYNC, VideoPacing.decide(0, 5_001,
			VideoPacing.NO_FRAME, INTERVAL, 1500, 5_000));
		assertEquals(Action.RESYNC, VideoPacing.decide(0, 3_600_000,
			VideoPacing.NO_FRAME, INTERVAL, 1500, 5_000));
	}

	/**
	 * 落后超过容忍值就重新定位，正好等于容忍值时不算：边界算「还能救」，
	 * 免得在容忍值附近反复跳。
	 */
	@Test
	void fallingBehindResyncs()
	{
		assertTrue(VideoPacing.isBehind(2000, 499, 1500));
		assertFalse(VideoPacing.isBehind(2000, 500, 1500));

		assertEquals(Action.RESYNC, VideoPacing.decide(10_000, 1000, 900,
			INTERVAL, 1500, 5_000));

		// 还没到点的帧不该被判成落后
		assertFalse(VideoPacing.isBehind(1000, 1500, 1500));
	}

	/** 落后时优先重新定位，而不是先睡一觉——那一觉可能长达几十秒。 */
	@Test
	void resyncWinsOverWaiting()
	{
		assertEquals(Action.RESYNC, VideoPacing.decide(60_000, 45_000,
			VideoPacing.NO_FRAME, INTERVAL, 1500, 5_000));
	}

	/** 落后但没超容忍值：该显示就显示，不该显示就丢。 */
	@Test
	void slightlyLateFramesAreStillShown()
	{
		assertEquals(Action.SHOW,
			VideoPacing.decide(1100, 1000, 900, INTERVAL, 1500, 5_000));
		assertEquals(Action.DROP,
			VideoPacing.decide(1100, 1010, 1000, INTERVAL, 1500, 5_000));
	}

	@Test
	void loopedTimeWrapsWithinOnePass()
	{
		assertEquals(0, VideoPacing.loopedTime(0, 10_000));
		assertEquals(9_999, VideoPacing.loopedTime(9_999, 10_000));
		assertEquals(0, VideoPacing.loopedTime(10_000, 10_000));
		assertEquals(1, VideoPacing.loopedTime(10_001, 10_000));
		assertEquals(500, VideoPacing.loopedTime(120_500, 10_000));
	}

	/**
	 * 时长缺失或者坏掉（0、负数）时一律当作第 0 毫秒：宁可停住第一帧，也不能除出
	 * 异常或者算出负的位置。
	 */
	@Test
	void degenerateDurationsDoNotBreakTheLoop()
	{
		assertEquals(0, VideoPacing.loopedTime(5_000, 0));
		assertEquals(0, VideoPacing.loopedTime(5_000, -100));
		assertEquals(5_000, VideoPacing.loopBase(5_000, 0));
	}

	/** 起点 + 轮内位置必须正好回到挂钟时间，否则跳一次画面就会偏一段。 */
	@Test
	void loopBaseKeepsTheTimelineContinuous()
	{
		long[] elapsed = {0, 1, 9_999, 10_000, 23_456, 1_000_000};
		long[] duration = {10_000, 3_333, 40};

		for(long now : elapsed)
			for(long pass : duration)
				assertEquals(now, VideoPacing.loopBase(now, pass)
					+ VideoPacing.loopedTime(now, pass));
	}

	/** 负数挂钟（时钟回拨）也要折进 [0, 时长) 里。 */
	@Test
	void negativeElapsedStillLandsInsideThePass()
	{
		long position = VideoPacing.loopedTime(-1, 10_000);

		assertEquals(9_999, position);
		assertTrue(position >= 0 && position < 10_000);
	}
}
