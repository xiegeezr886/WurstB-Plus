/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 锁住圆盘最容易写错的三件事：角度到扇区的映射（含跨越 0° 与缝隙）、死区/越界的
 * 取消、以及长按阈值。绘制错了看得出来，选中错了看不出来。
 */
public final class RadialMenuStateTest
{
	private static final float DELTA = 0.001F;
	private static final float RADIUS = 100F;
	
	private static RadialMenuState four()
	{
		return new RadialMenuState(List.of("a", "b", "c", "d"));
	}
	
	/** 极坐标转指针偏移（0° = 正上方，顺时针）。 */
	private static double[] at(double angleDegrees, double distance)
	{
		double radians = Math.toRadians(angleDegrees);
		return new double[]{distance * Math.sin(radians),
			-distance * Math.cos(radians)};
	}
	
	private static int sliceAtAngle(RadialMenuState state, double angle,
		double distance)
	{
		double[] pointer = at(angle, distance);
		return state.sliceAt(pointer[0], pointer[1], RADIUS);
	}
	
	@Test
	public void mapsTheFourCardinalDirections()
	{
		RadialMenuState state = four();
		
		// 0° 正上方是第 0 片的中心
		assertEquals(0, sliceAtAngle(state, 0, 90));
		assertEquals(1, sliceAtAngle(state, 90, 90));
		assertEquals(2, sliceAtAngle(state, 180, 90));
		assertEquals(3, sliceAtAngle(state, 270, 90));
	}
	
	@Test
	public void everySliceOwnsItsOwnQuadrant()
	{
		RadialMenuState state = four();
		
		for(int index = 0; index < 4; index++)
		{
			double centre = index * 90D;
			
			assertEquals(index, sliceAtAngle(state, centre, 90),
				"中心角 " + centre + " 应该选中第 " + index + " 片");
			// 中心两侧各 40° 仍在同一片内
			assertEquals(index, sliceAtAngle(state, centre - 40, 90));
			assertEquals(index, sliceAtAngle(state, centre + 40, 90));
		}
	}
	
	@Test
	public void theGapBetweenSlicesSelectsNothing()
	{
		RadialMenuState state = four();
		
		// 45° 正好落在第 0 片与第 1 片之间的缝隙上
		assertEquals(-1, sliceAtAngle(state, 45, 99));
		assertEquals(-1, sliceAtAngle(state, 135, 99));
		assertEquals(-1, sliceAtAngle(state, 225, 99));
		assertEquals(-1, sliceAtAngle(state, 315, 99));
	}
	
	@Test
	public void theDeadZoneAndTheOutsideSelectNothing()
	{
		RadialMenuState state = four();
		
		// 死区内：可以反悔
		assertEquals(-1, sliceAtAngle(state, 0, RADIUS * 0.3));
		assertEquals(-1, state.sliceAt(0, 0, RADIUS));
		// 外半径之外
		assertEquals(-1, sliceAtAngle(state, 0, RADIUS + 1));
	}
	
	@Test
	public void sliceGeometryIsEvenlySpaced()
	{
		RadialMenuState state = four();
		
		assertEquals(0F, state.sliceCentre(0), DELTA);
		assertEquals(90F, state.sliceCentre(1), DELTA);
		assertEquals(180F, state.sliceCentre(2), DELTA);
		assertEquals(270F, state.sliceCentre(3), DELTA);
		// 起点在半格之前
		assertEquals(-45F, state.sliceStart(0), DELTA);
		assertEquals(45F, state.sliceStart(1), DELTA);
		// 扫过角度扣掉了缝隙
		assertEquals(88F, state.sliceSweep(), DELTA);
	}
	
	@Test
	public void sliceCountIsClamped()
	{
		assertEquals(2,
			new RadialMenuState(List.of("only")).sliceCount());
		assertEquals(RadialMenuState.MAX_SLICES,
			new RadialMenuState(List.of("1", "2", "3", "4", "5", "6", "7", "8",
				"9", "10", "11", "12", "13", "14")).sliceCount());
		assertEquals(4, four().sliceCount());
	}
	
	@Test
	public void emptyMenuNeverSelects()
	{
		RadialMenuState state = new RadialMenuState(List.of());
		
		assertEquals(-1, state.sliceAt(0, -90, RADIUS));
	}
	
	@Test
	public void longPressOpensAtTheThreshold()
	{
		RadialMenuState state = four();
		state.press(0L);
		
		assertFalse(state.update(0L, true, 0, -90, RADIUS));
		assertFalse(state.isOpen());
		
		assertFalse(
			state.update(RadialMenuState.HOLD_DELAY_MS - 1, true, 0, -90,
				RADIUS));
		assertFalse(state.isOpen());
		
		// 到点这一帧才打开，并回报"刚刚打开"
		assertTrue(state.update(RadialMenuState.HOLD_DELAY_MS, true, 0, -90,
			RADIUS));
		assertTrue(state.isOpen());
	}
	
	@Test
	public void aShortPressNeverOpens()
	{
		RadialMenuState state = four();
		state.press(0L);
		
		state.update(60L, true, 0, -90, RADIUS);
		state.update(80L, false, 0, -90, RADIUS);
		
		assertFalse(state.isOpen());
		assertEquals(-1, state.commit());
	}
	
	@Test
	public void selectedFollowsThePointerAndCommits()
	{
		RadialMenuState state = four();
		state.press(0L);
		state.update(RadialMenuState.HOLD_DELAY_MS, true, 0, -90, RADIUS);
		
		// 指针转到右边
		double[] right = at(90, 90);
		state.update(RadialMenuState.HOLD_DELAY_MS + 16, true, right[0],
			right[1], RADIUS);
		assertEquals(1, state.selected());
		
		assertEquals(1, state.commit());
		assertFalse(state.isOpen());
		assertEquals(-1, state.selected());
	}
	
	@Test
	public void releasingInTheDeadZoneCancels()
	{
		RadialMenuState state = four();
		state.press(0L);
		state.update(RadialMenuState.HOLD_DELAY_MS, true, 0, -90, RADIUS);
		
		// 指针回到中心
		state.update(RadialMenuState.HOLD_DELAY_MS + 16, true, 0, 0, RADIUS);
		assertEquals(-1, state.selected());
		assertEquals(-1, state.commit());
	}
	
	@Test
	public void progressRunsFromZeroToOneAndBack()
	{
		RadialMenuState state = four();
		state.press(0L);
		state.update(RadialMenuState.HOLD_DELAY_MS, true, 0, -90, RADIUS);
		long openedAt = RadialMenuState.HOLD_DELAY_MS;
		
		assertEquals(0F, state.progress(openedAt), DELTA);
		assertEquals(1F, state.progress(openedAt + RadialMenuState.OPEN_MS),
			DELTA);
		assertEquals(1F, state.progress(openedAt + RadialMenuState.OPEN_MS * 5),
			DELTA);
		
		// 中途的进度应在 0..1 之间
		float middle =
			state.progress(openedAt + RadialMenuState.OPEN_MS / 2);
		assertTrue(middle > 0F && middle < 1F, "中途进度 " + middle);
		
		// 松手后收回：从展开开始退，不跳变
		long releasedAt = openedAt + 1_000L;
		state.update(releasedAt, false, 0, -90, RADIUS);
		assertEquals(1F, state.progress(releasedAt), DELTA);
		assertTrue(state.progress(releasedAt + RadialMenuState.CLOSE_MS / 2) < 1F);
		assertEquals(0F,
			state.progress(releasedAt + RadialMenuState.CLOSE_MS), DELTA);
	}
	
	@Test
	public void closingBeforeOpeningStaysClosed()
	{
		RadialMenuState state = four();
		
		state.close(0L);
		assertFalse(state.isOpen());
		assertEquals(0F, state.progress(0L), DELTA);
	}
	
	@Test
	public void angleHelpersWrapCorrectly()
	{
		assertEquals(0D, RadialMenuState.wrap360(360D), DELTA);
		assertEquals(350D, RadialMenuState.wrap360(-10D), DELTA);
		assertEquals(10D, RadialMenuState.wrap360(370D), DELTA);
		assertEquals(0D, RadialMenuState.wrap180(360D), DELTA);
		assertEquals(-10D, RadialMenuState.wrap180(350D), DELTA);
		assertEquals(170D, RadialMenuState.wrap180(170D), DELTA);
	}
}
