/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hud2.elements;

import java.util.List;

import net.wurstclient.music.LyricLine;

/**
 * 音乐灵动岛的状态机：紧凑胶囊 ⇄ 展开卡片，以及紧凑态里轮换显示的内容。
 *
 * <p>
 * 交互设计参考 WinIslandProject/WinIsland（Windows 桌面灵动岛，Rust）：它的核心
 * 不是画法，而是**多状态 + 事件驱动**——平时是紧凑胶囊，悬停或事件（新歌、通知、
 * 音量）到达时展开成大卡片，事件结束后自动收回（见其 {@code src/ui/compact/mod.rs}
 * 与 {@code src/ui/expanded/music_view.rs}）。本工程原来的
 * {@link MusicIslandHudElement} 只有一条固定单行胶囊，只会横向变宽，没有状态。
 *
 * <p>
 * 这里把状态、时序与插值全部做成**只依赖传入的毫秒时间**的纯逻辑，不碰 Minecraft，
 * 因此整条时间线（悬停延迟、新歌脉冲、收回、轮换）都能用单测逐帧驱动验证——
 * 这类"动画看着不对"的 bug 靠编译是发现不了的。
 */
public final class MusicIslandState
{
	/** 紧凑态：单行胶囊。 */
	public static final int COMPACT_MIN_WIDTH = 94;
	public static final int COMPACT_MAX_WIDTH = 250;
	public static final int COMPACT_HEIGHT = 20;
	public static final float COMPACT_RADIUS = 10F;
	
	/** 展开态：封面 + 标题 + 进度 + 一句歌词。 */
	public static final int EXPANDED_WIDTH = 232;
	public static final int EXPANDED_HEIGHT = 78;
	public static final float EXPANDED_RADIUS = 14F;
	
	/** 时序（毫秒）。 */
	public static final long MORPH_MS = 220L;
	public static final long COLLAPSE_DELAY_MS = 450L;
	public static final long SONG_CHANGE_PULSE_MS = 3_000L;
	public static final long ROTATE_MS = 4_000L;
	
	/** 紧凑态显示的内容。 */
	public enum CompactContent
	{
		/** 正在播放音乐时的内容，优先级最高。 */
		MUSIC,
		FPS,
		TIME,
		MEMORY
	}
	
	private static final CompactContent[] ROTATION =
		{CompactContent.FPS, CompactContent.TIME, CompactContent.MEMORY};
	
	private boolean expanded;
	private float morphFrom;
	private long morphStart;
	private long morphDuration = MORPH_MS;
	
	private boolean hovered;
	private long hoverEndMs = Long.MIN_VALUE;
	private long pulseUntil = Long.MIN_VALUE;
	private long lastSongId = -1L;
	
	private long rotationStart = Long.MIN_VALUE;
	private int rotationIndex;
	
	/**
	 * 推进一帧。
	 *
	 * @param nowMs   单调递增的毫秒时间
	 * @param hovered 光标是否在岛上
	 * @param playing 是否正在播放
	 * @param songId  当前歌曲 id，用于识别换歌；没有歌传 0
	 */
	public void update(long nowMs, boolean hovered, boolean playing,
		long songId)
	{
		if(rotationStart == Long.MIN_VALUE)
			rotationStart = nowMs;
		
		// 换歌时脉冲展开一次，让用户看到换了什么
		if(songId != lastSongId)
		{
			lastSongId = songId;
			
			if(songId > 0L && playing)
				pulseUntil = nowMs + SONG_CHANGE_PULSE_MS;
		}
		
		if(hovered && !this.hovered)
			hoverEndMs = Long.MIN_VALUE;
		
		if(!hovered && this.hovered)
			hoverEndMs = nowMs;
		
		this.hovered = hovered;
		
		boolean wantExpanded = hovered || nowMs < pulseUntil
			|| hoverEndMs != Long.MIN_VALUE
				&& nowMs - hoverEndMs < COLLAPSE_DELAY_MS;
		
		if(wantExpanded != expanded)
		{
			// 从"当前实际值"接着走，中途反向不会跳变
			morphFrom = morph(nowMs);
			morphStart = nowMs;
			morphDuration = MORPH_MS;
			expanded = wantExpanded;
		}
		
		if(nowMs - rotationStart >= ROTATE_MS)
		{
			// 落后很多帧时也要按整数倍推进，避免长时间卡顿后只前进一格
			long steps = (nowMs - rotationStart) / ROTATE_MS;
			rotationIndex += (int)steps;
			rotationStart += steps * ROTATE_MS;
		}
	}
	
	/** 展开进度：0 全紧凑，1 全展开。 */
	public float morph(long nowMs)
	{
		float target = expanded ? 1F : 0F;
		float elapsed = nowMs - morphStart;
		
		if(morphDuration <= 0L || elapsed >= morphDuration)
			return target;
		
		if(elapsed <= 0L)
			return morphFrom;
		
		return morphFrom
			+ (target - morphFrom) * easeOutCubic(elapsed / (float)morphDuration);
	}
	
	public int width(long nowMs, int compactWidth)
	{
		int compact = Math.max(COMPACT_MIN_WIDTH,
			Math.min(COMPACT_MAX_WIDTH, compactWidth));
		return Math.round(compact
			+ (EXPANDED_WIDTH - compact) * morph(nowMs));
	}
	
	public int height(long nowMs)
	{
		return Math.round(COMPACT_HEIGHT
			+ (EXPANDED_HEIGHT - COMPACT_HEIGHT) * morph(nowMs));
	}
	
	public float radius(long nowMs)
	{
		return COMPACT_RADIUS
			+ (EXPANDED_RADIUS - COMPACT_RADIUS) * morph(nowMs);
	}
	
	/**
	 * 紧凑态显示哪一项：在放歌时音乐优先，否则在 FPS / 时间 / 内存之间轮换。
	 */
	public CompactContent compactContent(boolean playing, boolean hasSong)
	{
		if(playing && hasSong)
			return CompactContent.MUSIC;
		
		int index = Math.floorMod(rotationIndex, ROTATION.length);
		return ROTATION[index];
	}
	
	public boolean isExpanded()
	{
		return expanded;
	}
	
	public boolean isHovered()
	{
		return hovered;
	}
	
	/** 供测试与调试：当前轮换到第几格。 */
	public int rotationIndex()
	{
		return rotationIndex;
	}
	
	/**
	 * 当前该显示哪一行歌词：最后一条开始时间不晚于播放位置的。
	 * 还没有歌词时返回 -1。
	 */
	public static int currentLyricIndex(List<LyricLine> lyrics,
		long positionMs)
	{
		if(lyrics == null || lyrics.isEmpty())
			return -1;
		
		int found = -1;
		
		for(int i = 0; i < lyrics.size(); i++)
		{
			if(lyrics.get(i).timeMs() > positionMs)
				break;
			
			found = i;
		}
		
		return found;
	}
	
	/** 进度 0..1；时长非法时返回 0，避免除零产生 NaN。 */
	public static float progress(long positionMs, long durationMs)
	{
		if(durationMs <= 0L)
			return 0F;
		
		return Math.max(0F, Math.min(1F, positionMs / (float)durationMs));
	}
	
	private static float easeOutCubic(float t)
	{
		float clamped = Math.max(0F, Math.min(1F, t));
		float inverse = 1F - clamped;
		return 1F - inverse * inverse * inverse;
	}
}
