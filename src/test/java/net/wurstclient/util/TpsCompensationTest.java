/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 锁住 TPS 换算的方向与边界。方向写反（把卡顿时的延迟缩短而不是拉长）编译完全正常，
 * 只会让所有冷却在 laggy 服务器上集体偏快，属于最难发现的那类错误。
 */
public final class TpsCompensationTest
{
	private static final double DELTA = 0.0001D;
	
	@Test
	public void atTwentyTpsNothingChanges()
	{
		assertEquals(500D, TpsCompensation.scaleMillis(500D, 20D), DELTA);
		assertEquals(0D, TpsCompensation.scaleMillis(0D, 20D), DELTA);
	}
	
	@Test
	public void lagStretchesTheDelay()
	{
		// 10 TPS：每秒只有 10 个 tick，同样"等 500ms"只等于 5 个 tick，所以要等双倍
		assertEquals(1_000D, TpsCompensation.scaleMillis(500D, 10D), DELTA);
		assertEquals(2_000D, TpsCompensation.scaleMillis(500D, 5D), DELTA);
		// 越卡越长，单调
		assertTrue(TpsCompensation.scaleMillis(500D, 8D)
			> TpsCompensation.scaleMillis(500D, 16D));
	}
	
	@Test
	public void overTickingShortensTheDelay()
	{
		// 服务端超频到 40 TPS 时，同样时间里的 tick 数翻倍
		assertEquals(250D, TpsCompensation.scaleMillis(500D, 40D), DELTA);
	}
	
	@Test
	public void unmeasuredTpsMeansNoCompensation()
	{
		// 刚换世界时可能读到 0，或还没测到；这时宁可原样返回
		assertEquals(500D, TpsCompensation.scaleMillis(500D, 0D), DELTA);
		assertEquals(500D,
			TpsCompensation.scaleMillis(500D, Double.NaN), DELTA);
		assertEquals(500D,
			TpsCompensation.scaleMillis(500D, Double.POSITIVE_INFINITY), DELTA);
		assertEquals(500D, TpsCompensation.scaleMillis(500D, 0.5D), DELTA);
	}
	
	@Test
	public void negativeMillisScaleUniformlyAndStayNegative()
	{
		// 没有为负值开特例：它照样按 tick 速率缩放，但仍然是负数，
		// 所以调用方的 `> 0` 判断不会被破坏
		assertEquals(-200D, TpsCompensation.scaleMillis(-100D, 10D), DELTA);
		assertEquals(-100D, TpsCompensation.scaleMillis(-100D, 20D), DELTA);
		assertTrue(TpsCompensation.scaleMillis(-100D, 10D) < 0D);
	}
	
	@Test
	public void ticksAndMillisAreInverses()
	{
		for(double tps : new double[]{1D, 5D, 10D, 20D})
			for(double millis : new double[]{0D, 250D, 500D, 1_234D})
			{
				double ticks = TpsCompensation.ticksFor(millis, tps);
				assertEquals(millis,
					TpsCompensation.millisForTicks(ticks, tps), 0.0001D);
			}
	}
	
	@Test
	public void ticksForFollowsTheTickRate()
	{
		// 20 TPS 下 1000ms 正好 20 tick
		assertEquals(20D, TpsCompensation.ticksFor(1_000D, 20D), DELTA);
		// 10 TPS 下同样的 1000ms 只有 10 tick
		assertEquals(10D, TpsCompensation.ticksFor(1_000D, 10D), DELTA);
		// 未测量时按参考速率算
		assertEquals(20D, TpsCompensation.ticksFor(1_000D, 0D), DELTA);
	}
	
	@Test
	public void millisForTicksFollowsTheTickRate()
	{
		assertEquals(50D, TpsCompensation.millisForTicks(1D, 20D), DELTA);
		assertEquals(100D, TpsCompensation.millisForTicks(1D, 10D), DELTA);
	}
	
	@Test
	public void isMeasuredRejectsUnusableValues()
	{
		assertTrue(TpsCompensation.isMeasured(1D));
		assertTrue(TpsCompensation.isMeasured(20D));
		assertFalse(TpsCompensation.isMeasured(0D));
		assertFalse(TpsCompensation.isMeasured(0.99D));
		assertFalse(TpsCompensation.isMeasured(-5D));
		assertFalse(TpsCompensation.isMeasured(Double.NaN));
	}
	
	@Test
	public void sanitizeClampsIntoTheUsableRange()
	{
		assertEquals(20D, TpsCompensation.sanitize(99D), DELTA);
		assertEquals(1D, TpsCompensation.sanitize(0D), DELTA);
		assertEquals(1D, TpsCompensation.sanitize(-3D), DELTA);
		assertEquals(20D, TpsCompensation.sanitize(Double.NaN), DELTA);
		assertEquals(12.5D, TpsCompensation.sanitize(12.5D), DELTA);
	}
}
