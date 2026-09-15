/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public final class TwilightCornerMaskTest
{
	@Test
	public void keepsEverythingWithoutARadius()
	{
		assertEquals(255, TwilightCornerMask.coverage(0, 0, 100, 0));
		assertEquals(255, TwilightCornerMask.coverage(99, 99, 100, 0));
		assertEquals(0, TwilightCornerMask.clampRadius(0, 100));
		assertEquals(0, TwilightCornerMask.clampRadius(-5, 100));
	}

	@Test
	public void clearsTheCornerPixels()
	{
		assertEquals(0, TwilightCornerMask.coverage(0, 0, 100, 20));
		assertEquals(0, TwilightCornerMask.coverage(99, 0, 100, 20));
		assertEquals(0, TwilightCornerMask.coverage(0, 99, 100, 20));
		assertEquals(0, TwilightCornerMask.coverage(99, 99, 100, 20));
	}

	@Test
	public void keepsTheCentreAndEdgesOpaque()
	{
		assertEquals(255, TwilightCornerMask.coverage(50, 50, 100, 20));
		// 边中点不在任何圆角方块内
		assertEquals(255, TwilightCornerMask.coverage(0, 50, 100, 20));
		assertEquals(255, TwilightCornerMask.coverage(50, 0, 100, 20));
		assertEquals(255, TwilightCornerMask.coverage(99, 50, 100, 20));
		assertEquals(255, TwilightCornerMask.coverage(50, 99, 100, 20));
		// 圆角方块的边界之外就已经是整像素
		assertEquals(255, TwilightCornerMask.coverage(20, 0, 100, 20));
		assertEquals(255, TwilightCornerMask.coverage(0, 20, 100, 20));
	}

	@Test
	public void antiAliasesTheCornerEdge()
	{
		boolean partial = false;
		
		for(int y = 0; y < 24 && !partial; y++)
			for(int x = 0; x < 24; x++)
			{
				int coverage = TwilightCornerMask.coverage(x, y, 100, 20);
				
				if(coverage > 0 && coverage < 255)
				{
					partial = true;
					break;
				}
			}
		
		assertTrue(partial, "圆角边缘应该出现半透明像素");
	}

	@Test
	public void isSymmetric()
	{
		for(int y = 0; y < 32; y++)
			for(int x = 0; x < 32; x++)
			{
				int coverage = TwilightCornerMask.coverage(x, y, 100, 20);
				
				assertEquals(coverage,
					TwilightCornerMask.coverage(y, x, 100, 20));
				assertEquals(coverage,
					TwilightCornerMask.coverage(99 - x, y, 100, 20));
				assertEquals(coverage,
					TwilightCornerMask.coverage(x, 99 - y, 100, 20));
				assertEquals(coverage,
					TwilightCornerMask.coverage(99 - x, 99 - y, 100, 20));
			}
	}

	@Test
	public void monotonicallyFillsTowardsTheCentre()
	{
		int previous = -1;
		
		// 沿对角线走向中心，覆盖率只能不减
		for(int i = 0; i < 20; i++)
		{
			int coverage = TwilightCornerMask.coverage(i, i, 100, 20);
			assertTrue(coverage >= previous,
				"第 " + i + " 步覆盖率回退了");
			previous = coverage;
		}
	}

	@Test
	public void clampsOversizedRadii()
	{
		assertEquals(50, TwilightCornerMask.clampRadius(80, 100));
		assertEquals(20, TwilightCornerMask.clampRadius(20, 100));
		// 半径过半时退化成圆形，但中心与边中点仍然是不透明的
		assertEquals(255, TwilightCornerMask.coverage(50, 50, 100, 80));
		assertEquals(0, TwilightCornerMask.coverage(0, 0, 100, 80));
	}

	@Test
	public void treatsOutOfBoundsAsEmpty()
	{
		assertEquals(0, TwilightCornerMask.coverage(0, 0, 0, 10));
		assertEquals(0, TwilightCornerMask.coverage(-1, 0, 100, 20));
		assertEquals(0, TwilightCornerMask.coverage(100, 0, 100, 20));
		assertEquals(0, TwilightCornerMask.coverage(0, -1, 100, 20));
		assertEquals(0, TwilightCornerMask.coverage(0, 100, 100, 20));
	}

	@Test
	public void detectsCornerSquares()
	{
		assertTrue(TwilightCornerMask.isCorner(0, 0, 100, 20));
		assertTrue(TwilightCornerMask.isCorner(99, 99, 100, 20));
		assertFalse(TwilightCornerMask.isCorner(20, 0, 100, 20));
		assertFalse(TwilightCornerMask.isCorner(0, 20, 100, 20));
		assertFalse(TwilightCornerMask.isCorner(50, 50, 100, 20));
	}

	@Test
	public void scalesAlphaAndKeepsColour()
	{
		assertEquals(0xFF3366CC,
			TwilightCornerMask.applyAlpha(0xFF3366CC, 255));
		assertEquals(0x003366CC, TwilightCornerMask.applyAlpha(0xFF3366CC, 0));
		assertEquals(0x403366CC,
			TwilightCornerMask.applyAlpha(0x803366CC, 128));
		assertEquals(0x333366CC,
			TwilightCornerMask.applyAlpha(0xFF3366CC, 51));
		// 已经半透明的像素按比例再乘一次
		assertEquals(0x203366CC,
			TwilightCornerMask.applyAlpha(0x403366CC, 128));
		// 覆盖率超出范围时不应该溢出到颜色通道
		assertEquals(0xFF3366CC,
			TwilightCornerMask.applyAlpha(0xFF3366CC, 999));
	}

	@Test
	public void applyAlphaNeverTouchesColourChannels()
	{
		for(int coverage = 0; coverage <= 255; coverage += 5)
		{
			int masked = TwilightCornerMask.applyAlpha(0x7F123456, coverage);
			assertEquals(0x123456, masked & 0x00FFFFFF,
				"覆盖率 " + coverage + " 改动了颜色");
		}
	}
}
