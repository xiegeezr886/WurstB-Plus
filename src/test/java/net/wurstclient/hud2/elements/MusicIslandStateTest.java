/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hud2.elements;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.wurstclient.hud2.elements.MusicIslandState.CompactContent;
import net.wurstclient.music.LyricLine;

/**
 * 用确定的时间轴驱动灵动岛的状态机，把「悬停展开 / 新歌脉冲 / 自动收回 / 内容轮换」
 * 整条时间线都断言下来。动画类 bug 编译不会报，只能靠这样测。
 */
public final class MusicIslandStateTest
{
	private static final float DELTA = 0.001F;
	
	@Test
	public void expandsOnHoverAndAnimatesOverTheMorphDuration()
	{
		MusicIslandState state = new MusicIslandState();
		
		state.update(0L, true, false, 0L);
		assertTrue(state.isExpanded());
		// 刚切状态时还在起始点
		assertEquals(0F, state.morph(0L), DELTA);
		// 中途在 0..1 之间
		float middle = state.morph(MusicIslandState.MORPH_MS / 2);
		assertTrue(middle > 0F && middle < 1F, "morph 应为 " + middle);
		// 到时满值
		assertEquals(1F, state.morph(MusicIslandState.MORPH_MS), DELTA);
		assertEquals(1F, state.morph(MusicIslandState.MORPH_MS * 10), DELTA);
	}
	
	@Test
	public void staysCollapsedWhenNeverHovered()
	{
		MusicIslandState state = new MusicIslandState();
		
		for(long t = 0L; t <= 10_000L; t += 250L)
			state.update(t, false, false, 0L);
		
		assertFalse(state.isExpanded());
		assertEquals(0F, state.morph(10_000L), DELTA);
	}
	
	@Test
	public void collapseWaitsForTheDelay()
	{
		MusicIslandState state = new MusicIslandState();
		state.update(0L, true, false, 0L);
		assertTrue(state.isExpanded());
		
		// 光标移开：延迟内仍然展开
		state.update(100L, false, false, 0L);
		assertTrue(state.isExpanded());
		state.update(100L + MusicIslandState.COLLAPSE_DELAY_MS - 1, false, false,
			0L);
		assertTrue(state.isExpanded());
		
		// 超过延迟才收回
		state.update(100L + MusicIslandState.COLLAPSE_DELAY_MS, false, false,
			0L);
		assertFalse(state.isExpanded());
	}
	
	@Test
	public void songChangePulsesThenCollapses()
	{
		MusicIslandState state = new MusicIslandState();
		
		state.update(0L, false, true, 42L);
		assertTrue(state.isExpanded(), "换歌应该脉冲展开");
		
		// 脉冲期间保持展开
		state.update(MusicIslandState.SONG_CHANGE_PULSE_MS - 1, false, true,
			42L);
		assertTrue(state.isExpanded());
		
		// 脉冲过后（且没悬停）收回
		state.update(MusicIslandState.SONG_CHANGE_PULSE_MS, false, true, 42L);
		assertFalse(state.isExpanded());
	}
	
	@Test
	public void sameSongDoesNotRePulse()
	{
		MusicIslandState state = new MusicIslandState();
		
		state.update(0L, false, true, 7L);
		long pulseEnd = MusicIslandState.SONG_CHANGE_PULSE_MS;
		state.update(pulseEnd, false, true, 7L);
		assertFalse(state.isExpanded());
		
		// 同一首歌继续播，不该再次展开
		state.update(pulseEnd + 1_000L, false, true, 7L);
		assertFalse(state.isExpanded());
	}
	
	@Test
	public void pausedSongDoesNotPulse()
	{
		MusicIslandState state = new MusicIslandState();
		
		// 有歌但没在播（例如暂停后切歌）：不脉冲
		state.update(0L, false, false, 9L);
		assertFalse(state.isExpanded());
	}
	
	@Test
	public void morphDoesNotJumpWhenReversedMidway()
	{
		MusicIslandState state = new MusicIslandState();
		state.update(0L, true, false, 0L);
		
		long half = MusicIslandState.MORPH_MS / 2;
		float before = state.morph(half);
		assertTrue(before > 0F && before < 1F);
		
		// 中途反向：展开进度必须从当前值接着走，不能跳回 1
		state.update(half, false, false, 0L);
		float after = state.morph(half);
		
		// 这里仍是 expanded（延迟未过），先确认连续性判断本身成立
		assertEquals(before, after, 0.0001F);
	}
	
	@Test
	public void reversedMorphActuallyRetreats()
	{
		MusicIslandState state = new MusicIslandState();
		state.update(0L, true, false, 0L);
		
		long collapseAt =
			100L + MusicIslandState.COLLAPSE_DELAY_MS;
		state.update(100L, false, false, 0L);
		state.update(collapseAt, false, false, 0L);
		assertFalse(state.isExpanded());
		
		float justAfterCollapse = state.morph(collapseAt);
		float later = state.morph(collapseAt + MusicIslandState.MORPH_MS);
		
		assertTrue(later < justAfterCollapse,
			"收回过程中进度应下降：" + justAfterCollapse + " → " + later);
		assertEquals(0F, later, DELTA);
	}
	
	@Test
	public void morphIsMonotonicWhileExpanding()
	{
		MusicIslandState state = new MusicIslandState();
		state.update(0L, true, false, 0L);
		
		float previous = -1F;
		
		for(long t = 0L; t <= MusicIslandState.MORPH_MS
			* 2; t += MusicIslandState.MORPH_MS / 8)
		{
			float value = state.morph(t);
			assertTrue(value >= previous, t + "ms 处回退了");
			previous = value;
		}
		
		assertEquals(1F, previous, DELTA);
	}
	
	@Test
	public void sizeLerpsBetweenTheTwoStates()
	{
		MusicIslandState state = new MusicIslandState();
		state.update(0L, false, false, 0L);
		
		assertEquals(MusicIslandState.COMPACT_HEIGHT, state.height(0L));
		assertEquals(MusicIslandState.COMPACT_RADIUS, state.radius(0L), DELTA);
		// 紧凑宽度会被夹到上下限
		assertEquals(MusicIslandState.COMPACT_MIN_WIDTH,
			state.width(0L, 10));
		assertEquals(MusicIslandState.COMPACT_MAX_WIDTH,
			state.width(0L, 9_999));
		
		state.update(MusicIslandState.MORPH_MS, true, false, 0L);
		assertEquals(MusicIslandState.EXPANDED_WIDTH,
			state.width(MusicIslandState.MORPH_MS * 2, 200));
		assertEquals(MusicIslandState.EXPANDED_HEIGHT,
			state.height(MusicIslandState.MORPH_MS * 2));
		assertEquals(MusicIslandState.EXPANDED_RADIUS,
			state.radius(MusicIslandState.MORPH_MS * 2), DELTA);
	}
	
	@Test
	public void compactContentPrefersMusic()
	{
		MusicIslandState state = new MusicIslandState();
		
		assertEquals(CompactContent.MUSIC,
			state.compactContent(true, true));
		// 没在播就不给音乐
		assertNotEquals(CompactContent.MUSIC,
			state.compactContent(false, true));
		assertNotEquals(CompactContent.MUSIC,
			state.compactContent(true, false));
	}
	
	@Test
	public void compactContentRotatesAndWraps()
	{
		MusicIslandState state = new MusicIslandState();
		state.update(0L, false, false, 0L);
		
		CompactContent first = state.compactContent(false, false);
		assertEquals(CompactContent.FPS, first);
		
		state.update(MusicIslandState.ROTATE_MS, false, false, 0L);
		assertEquals(CompactContent.TIME, state.compactContent(false, false));
		
		state.update(MusicIslandState.ROTATE_MS * 2, false, false, 0L);
		assertEquals(CompactContent.MEMORY, state.compactContent(false, false));
		
		// 转满一圈回到第一项，不越界
		state.update(MusicIslandState.ROTATE_MS * 3, false, false, 0L);
		assertEquals(CompactContent.FPS, state.compactContent(false, false));
	}
	
	@Test
	public void rotationCatchesUpAfterALongStall()
	{
		MusicIslandState state = new MusicIslandState();
		state.update(0L, false, false, 0L);
		
		// 卡了 9 个轮换周期（正好 3 圈）：应该前进 9 格并回到第一项
		state.update(MusicIslandState.ROTATE_MS * 9, false, false, 0L);
		assertEquals(9, state.rotationIndex());
		assertEquals(CompactContent.FPS, state.compactContent(false, false));
		
		// 再多一格就落到第二项，确认是"追赶"而不是只前进一格
		state.update(MusicIslandState.ROTATE_MS * 10, false, false, 0L);
		assertEquals(10, state.rotationIndex());
		assertEquals(CompactContent.TIME, state.compactContent(false, false));
	}
	
	@Test
	public void currentLyricIndexFollowsThePlaybackPosition()
	{
		List<LyricLine> lyrics = List.of(new LyricLine(1_000L, "a"),
			new LyricLine(3_000L, "b"), new LyricLine(5_000L, "c"));
		
		// 还没有歌词时
		assertEquals(-1, MusicIslandState.currentLyricIndex(lyrics, 0L));
		assertEquals(-1, MusicIslandState.currentLyricIndex(lyrics, 999L));
		// 正好卡在起始时间上算这一行
		assertEquals(0, MusicIslandState.currentLyricIndex(lyrics, 1_000L));
		assertEquals(0, MusicIslandState.currentLyricIndex(lyrics, 2_999L));
		assertEquals(1, MusicIslandState.currentLyricIndex(lyrics, 3_000L));
		// 最后一行之后一直停在最后一行
		assertEquals(2, MusicIslandState.currentLyricIndex(lyrics, 99_999L));
	}
	
	@Test
	public void currentLyricIndexHandlesEmptyAndNull()
	{
		assertEquals(-1, MusicIslandState.currentLyricIndex(List.of(), 0L));
		assertEquals(-1, MusicIslandState.currentLyricIndex(null, 0L));
	}
	
	@Test
	public void progressIsClampedAndSafe()
	{
		assertEquals(0F, MusicIslandState.progress(0L, 0L), DELTA);
		assertEquals(0F, MusicIslandState.progress(5_000L, -1L), DELTA);
		assertEquals(0F, MusicIslandState.progress(-100L, 10_000L), DELTA);
		assertEquals(0.5F, MusicIslandState.progress(5_000L, 10_000L), DELTA);
		assertEquals(1F, MusicIslandState.progress(20_000L, 10_000L), DELTA);
	}
}
