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

import net.wurstclient.music.LyricLine;

/**
 * 用确定的时间轴驱动灵动岛的状态机，把「悬停展开 / 新歌脉冲 / 自动收回 / 尺寸弹簧 /
 * 紧凑宽度自适应」整条时间线都断言下来。动画类 bug 编译不会报，只能靠这样测。
 */
public final class MusicIslandStateTest
{
	private static final float DELTA = 0.001F;
	
	/** 按 60fps 把某段时间喂给状态机，模拟真实渲染帧。 */
	private static void advance(MusicIslandState state, long fromMs, long toMs,
		boolean hovered, boolean playing, long songId)
	{
		for(long now = fromMs; now <= toMs; now += 16L)
			state.update(now, hovered, playing, songId);
	}
	
	@Test
	public void expandsOnHoverAndAnimatesOverTheMorphDuration()
	{
		MusicIslandState state = new MusicIslandState();
		
		state.update(0L, true, false, 0L);
		assertTrue(state.isExpanded());
		// 刚切状态时弹簧还在起始点
		assertEquals(0F, state.morph(0L), DELTA);
		// 中途在 0..1 之间
		advance(state, 0L, 110L, true, false, 0L);
		float middle = state.morph(110L);
		assertTrue(middle > 0F && middle < 1F, "morph 应为 " + middle);
		// 一段时间后到达满值
		advance(state, 126L, 1_500L, true, false, 0L);
		assertEquals(1F, state.morph(1_500L), DELTA);
		assertEquals(1F, state.morph(10_000L), DELTA);
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
		
		advance(state, 0L, 110L, true, false, 0L);
		float before = state.morph(110L);
		assertTrue(before > 0F && before < 1F);
		
		// 中途反向：展开进度必须从当前值接着走，不能跳回 1
		state.update(110L, false, false, 0L);
		float after = state.morph(110L);
		
		// 这里仍是 expanded（延迟未过），先确认连续性判断本身成立
		assertEquals(before, after, 0.0001F);
	}
	
	@Test
	public void reversedMorphActuallyRetreats()
	{
		MusicIslandState state = new MusicIslandState();
		state.update(0L, true, false, 0L);
		
		long collapseAt = 100L + MusicIslandState.COLLAPSE_DELAY_MS;
		advance(state, 0L, 100L, true, false, 0L);
		// 精确地在 100ms 结束悬停：延迟从这一刻起算
		state.update(100L, false, false, 0L);
		state.update(collapseAt, false, false, 0L);
		assertFalse(state.isExpanded());
		
		float halfway = state.morph(collapseAt);
		assertTrue(halfway > 0F && halfway < 1F);
		
		// 弹簧有惯性，会先过冲一点再落回，所以比较的是"最终值"而不是单调下降
		advance(state, collapseAt + 16L, collapseAt + 1_500L, false, false, 0L);
		float later = state.morph(collapseAt + 1_500L);
		
		assertTrue(later < halfway,
			"收回最终值应低于收回瞬间：" + halfway + " → " + later);
		assertEquals(0F, later, DELTA);
	}
	
	@Test
	public void morphIsMonotonicWhileExpanding()
	{
		MusicIslandState state = new MusicIslandState();
		state.update(0L, true, false, 0L);
		
		float previous = -1F;
		
		// 弹簧会轻微过冲，但不得回退到上一帧之下
		for(long t = 0L; t <= 1_500L; t += 16L)
		{
			float value = state.morph(t);
			assertTrue(value >= previous - 0.02F, t + "ms 处回退过多");
			previous = value;
		}
		
		assertEquals(1F, previous, DELTA);
	}
	
	@Test
	public void springApproachesWithoutOvershooting()
	{
		MusicIslandState state = new MusicIslandState();
		state.update(0L, true, false, 0L);
		
		float peak = 0F;
		float previous = 0F;
		
		for(long t = 0L; t <= 2_000L; t += 16L)
		{
			float value = state.morph(t);
			assertTrue(value >= previous - 0.0001F,
				t + "ms 处弹簧回退了：" + previous + " → " + value);
			previous = value;
			peak = Math.max(peak, value);
		}
		
		// 规格 §3.1 把弹速夹在"剩余距离 × 0.2"内，弹簧因此不会越过目标
		assertTrue(peak <= 1F + DELTA, "过冲应该被弹速上限吃掉：" + peak);
		// 但必须在 ~0.3s 内基本到位，否则就是"看着卡"
		MusicIslandState early = new MusicIslandState();
		early.update(0L, true, false, 0L);
		
		for(long t = 0L; t <= 300L; t += 16L)
			early.morph(t);
		
		assertTrue(early.morph(300L) > 0.95F,
			"300ms 内应基本展开：" + early.morph(300L));
		assertEquals(1F, state.morph(2_000L), DELTA);
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
		
		state.update(0L, true, false, 0L);
		advance(state, 0L, 1_500L, true, false, 0L);
		assertEquals(MusicIslandState.EXPANDED_WIDTH,
			state.width(1_500L, 200));
		assertEquals(MusicIslandState.EXPANDED_HEIGHT,
			state.height(1_500L));
		assertEquals(MusicIslandState.EXPANDED_RADIUS,
			state.radius(1_500L), DELTA);
	}
	
	@Test
	public void compactContentAlphaCrossesOverBeforeTheEnd()
	{
		MusicIslandState state = new MusicIslandState();
		state.update(0L, false, false, 0L);
		
		assertEquals(1F, state.compactAlpha(0L), DELTA);
		assertEquals(0F, state.expandedAlpha(0L), DELTA);
		
		state.update(16L, true, false, 0L);
		advance(state, 16L, 1_500L, true, false, 0L);
		
		assertEquals(0F, state.compactAlpha(1_500L), DELTA);
		assertEquals(1F, state.expandedAlpha(1_500L), DELTA);
	}
	
	@Test
	public void compactWidthForFollowsTheSpanOfTheIsland()
	{
		// 依据规格 §5.2 的固定槽位，紧凑宽度按内容自适应并夹在两端之间
		assertEquals(MusicIslandState.COMPACT_MIN_WIDTH,
			MusicIslandState.compactWidthFor(0));
		assertEquals(MusicIslandState.COMPACT_MIN_WIDTH,
			MusicIslandState.compactWidthFor(
				MusicIslandState.COMPACT_MIN_WIDTH - 1));
		assertEquals(150, MusicIslandState.compactWidthFor(150));
		assertEquals(MusicIslandState.COMPACT_MAX_WIDTH,
			MusicIslandState.compactWidthFor(9_999));
	}
	
	@Test
	public void visualSmoothingIsAsymmetric()
	{
		// 上升 0.6 / 下降 0.08（规格 §6.4）：同一帧数下抬升远快于落下
		float risen = MusicIslandState.smoothVisual(0F, 1F, 1F / 60F);
		float fallen = MusicIslandState.smoothVisual(1F, 0F, 1F / 60F);
		
		assertEquals(0.6F, risen, 0.01F);
		assertEquals(0.92F, fallen, 0.01F);
		assertTrue(risen > 1F - fallen, "抬升必须快于回落");
	}
	
	@Test
	public void progressSmoothingSnapsOnJumps()
	{
		// 正常追赶：每帧走 0.15 的剩余距离
		assertEquals(0.415F,
			MusicIslandState.smoothProgress(0.4F, 0.5F, false), 0.001F);
		// 开场从 0 起步：直接吸附，避免指针从上次位置慢慢爬
		assertEquals(0.01F, MusicIslandState.smoothProgress(0.7F, 0.01F, false),
			DELTA);
		// 拖动跳转
		assertEquals(0.9F, MusicIslandState.smoothProgress(0.1F, 0.9F, false),
			DELTA);
		// 正在拖动：由调用方直写，这里原样返回
		assertEquals(0.9F, MusicIslandState.smoothProgress(0.1F, 0.9F, true),
			DELTA);
	}
	
	@Test
	public void accentFromAverageBrightensDarkCovers()
	{
		// 深色封面：放大 1.3 倍后仍达不到亮度下限 80，需要整体提亮
		int dark = MusicIslandState.accentFromAverage(0x101010, 1.3F);
		float darkLuminance = 0.299F * (dark >> 16 & 0xFF)
			+ 0.587F * (dark >> 8 & 0xFF) + 0.114F * (dark & 0xFF);
		assertTrue(darkLuminance >= 79.5F, "提亮后亮度应到 80：" + darkLuminance);
		assertEquals(0xFF, dark >>> 24);
		
		// 亮色封面：只放大，不触发提亮；0x80 * 1.5 = 0xC0
		assertEquals(0xFFFFFFFF,
			MusicIslandState.accentFromAverage(0xFFFFFF, 1.5F));
		assertEquals(0xFFC0C0C0,
			MusicIslandState.accentFromAverage(0x808080, 1.5F));
		
		// 辅色放大系数更大，因此比主色亮
		int primary = MusicIslandState.accentFromAverage(0x404040, 1.3F);
		int secondary = MusicIslandState.accentFromAverage(0x404040, 1.5F);
		assertNotEquals(primary, secondary);
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
