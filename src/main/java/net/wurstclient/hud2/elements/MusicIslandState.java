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
 * 音乐灵动岛的状态机：紧凑胶囊 ⇄ 展开卡片、形变弹簧、可视化柱平滑与歌词选择。
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
 * 因此整条时间线（悬停延迟、新歌脉冲、收回、形变）都能用单测逐帧驱动验证——
 * 这类"动画看着不对"的 bug 靠编译是发现不了的。
 *
 * <p>
 * 与 {@code _wi_ref/island-spec.md} 对齐的两处修正：<br>
 * 1. 紧凑态内容**不是按时间轮换**的，而是用户配置的固定槽位（规格 §5.2），所以
 * 原来的 {@code CompactContent} 轮换轴已删除，改由渲染层按槽位组装；<br>
 * 2. 形变由**弹簧**驱动（规格 §3.1，stiffness=0.10 / damping=0.68），而不是缓动曲线，
 * 这样中途反向时会自然减速再回弹，不会出现速度突变。
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
	public static final long COLLAPSE_DELAY_MS = 450L;
	public static final long SONG_CHANGE_PULSE_MS = 3_000L;
	
	/** 尺寸弹簧：规格 §3.1 的 stiffness=0.10 / damping=0.68。 */
	public static final float SIZE_STIFFNESS = 0.10F;
	public static final float SIZE_DAMPING = 0.68F;
	/** 弹簧速度上限 = 剩余距离 × 该系数（规格 §3.1）。 */
	public static final float SPRING_MAX_SPEED = 0.2F;
	/** 剩余距离小于该值就算到位，直接吸附，避免无限逼近。 */
	public static final float SPRING_SNAP = 0.0005F;
	/** 弹簧的积分基准帧率：规格的 stiffness/damping 是按 60fps 每帧给的。 */
	public static final float SPRING_BASE_FPS = 60F;
	
	/** 可视化柱：上升 0.6 / 下降 0.08（规格 §6.4，快起慢落）。 */
	public static final float VISUAL_ATTACK = 0.6F;
	public static final float VISUAL_RELEASE = 0.08F;
	
	/** 进度条：正常播放每帧 0.15，突出跳变或拖动时直接吸附（规格 §3.3）。 */
	public static final float PROGRESS_SMOOTH = 0.15F;
	public static final float PROGRESS_SNAP = 0.3F;
	
	private boolean expanded;
	private float morphValue;
	private float morphVelocity;
	private long morphUpdateMs = Long.MIN_VALUE;
	
	private boolean hovered;
	private long hoverEndMs = Long.MIN_VALUE;
	private long pulseUntil = Long.MIN_VALUE;
	private long lastSongId = -1L;
	
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
			expanded = wantExpanded;
		
		step(nowMs);
	}
	
	/**
	 * 把弹簧推进到 {@code nowMs}。纯读取方法也会调用它，因为形状属性的取值
	 * （宽/高/圆角）都从这里来，测试与渲染两条路径共用同一份积分。
	 */
	private void step(long nowMs)
	{
		if(morphUpdateMs == Long.MIN_VALUE)
		{
			// 第一帧只记录时间：起始值固定为 0（视觉上永远是"从紧凑开始"），
			// 这样开局那一帧不会因为已经处于展开态而直接跳到满值
			morphUpdateMs = nowMs;
			return;
		}
		
		float delta = Math.min(0.1F, (nowMs - morphUpdateMs) / 1000F);
		morphUpdateMs = Math.max(morphUpdateMs, nowMs);
		
		if(delta <= 0F)
			return;
		
		float target = expanded ? 1F : 0F;
		float distance = target - morphValue;
		
		// 只按"还差多少"吸附：速度门限会随帧率变化，容易让弹簧在高帧率下
		// 一直抖不到底
		if(Math.abs(distance) <= SPRING_SNAP)
		{
			morphValue = target;
			morphVelocity = 0F;
			return;
		}
		
		// 规格的 stiffness/damping 是按 60fps 每帧给出的，这里把 dt 换算成
		// "基准帧数"，因此 60fps 下与参考实现逐帧一致，其它帧率下也不会变速。
		float ticks = delta * SPRING_BASE_FPS;
		float force = distance * SIZE_STIFFNESS * ticks;
		morphVelocity =
			(morphVelocity + force) * (float)Math.pow(SIZE_DAMPING, ticks);
		
		// 速度上限是"剩余距离 × 0.2"，越接近目标越慢，因此弹簧不会越过目标
		float limit = Math.abs(distance) * SPRING_MAX_SPEED;
		morphVelocity = Math.max(-limit, Math.min(limit, morphVelocity));
		morphValue += morphVelocity * ticks;
	}
	
	/** 展开进度：0 全紧凑，1 全展开。 */
	public float morph(long nowMs)
	{
		step(nowMs);
		return clamp01(morphValue);
	}
	
	/**
	 * 紧凑内容透明度 = {@code clamp(1 - progress*1.5, 0, 1)}（规格 §6.3）：
	 * 展开进度到 2/3 时紧凑内容已经完全淡出。
	 */
	public float compactAlpha(long nowMs)
	{
		return clamp01(1F - morph(nowMs) * 1.5F);
	}
	
	/** 展开内容透明度 = {@code progress²}（规格 §6.3）。 */
	public float expandedAlpha(long nowMs)
	{
		float progress = morph(nowMs);
		return progress * progress;
	}
	
	/**
	 * 紧凑宽度：规格 §5.1 按歌词宽度自适应，两端夹在
	 * {@link #COMPACT_MIN_WIDTH} / {@link #COMPACT_MAX_WIDTH} 内。
	 */
	public static int compactWidthFor(int contentWidth)
	{
		return Math.max(COMPACT_MIN_WIDTH,
			Math.min(COMPACT_MAX_WIDTH, contentWidth));
	}
	
	public int width(long nowMs, int compactWidth)
	{
		int compact = compactWidthFor(compactWidth);
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
	 * 可视化柱的**非对称**平滑（规格 §6.4）：目标高于现值时用
	 * {@link #VISUAL_ATTACK} 快速抬起，低于现值时用 {@link #VISUAL_RELEASE}
	 * 缓慢落下，每帧都是指数逼近，因此与帧率无关。
	 *
	 * @param current 上一帧的高度（会被就地改写）
	 * @param target  目标高度
	 * @param delta   距上一帧的秒数
	 */
	public static float smoothVisual(float current, float target, float delta)
	{
		float rate = target > current ? VISUAL_ATTACK : VISUAL_RELEASE;
		float step = 1F - (float)Math.pow(1F - rate, delta * 60F);
		return current + (target - current) * step;
	}
	
	/**
	 * 进度条的平滑值（规格 §3.3）：正常播放慢慢追，跨过 0 或跳出很远时直接吸附；
	 * {@code dragging} 时由调用方直写，不走这里。
	 */
	public static float smoothProgress(float current, float raw,
		boolean dragging)
	{
		if(dragging)
			return raw;
		
		// 开场从 0 爬升，或用户拖动跳转：直接吸附，别让指针慢慢飞过去
		if(raw < 0.02F && current > 0.02F || Math.abs(raw - current) > PROGRESS_SNAP)
			return raw;
		
		return current + (raw - current) * PROGRESS_SMOOTH;
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
		
		return clamp01(positionMs / (float)durationMs);
	}
	
	/**
	 * 从封面取强调色（规格 §4.1 的第 3、4 步）：线性放大后算亮度，
	 * 亮度低于 80 就整体提亮到 80，保证深色封面上的柱不会糊在深色底里。
	 *
	 * @param rgb 未放大的封面平均色（0xRRGGBB）
	 * @param gain 主色 1.3，辅色 1.5
	 */
	public static int accentFromAverage(int rgb, float gain)
	{
		int red = Math.min(255,
			Math.round((rgb >> 16 & 0xFF) * gain));
		int green = Math.min(255,
			Math.round((rgb >> 8 & 0xFF) * gain));
		int blue = Math.min(255, Math.round((rgb & 0xFF) * gain));
		
		float luminance = 0.299F * red + 0.587F * green + 0.114F * blue;
		
		if(luminance < 80F)
		{
			int boost = Math.round(80F - luminance);
			red = Math.min(255, red + boost);
			green = Math.min(255, green + boost);
			blue = Math.min(255, blue + boost);
		}
		
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}
	
	public boolean isExpanded()
	{
		return expanded;
	}
	
	public boolean isHovered()
	{
		return hovered;
	}
	
	/** 供测试：当前弹簧速度（基准帧单位/帧）。 */
	public float morphVelocity()
	{
		return morphVelocity;
	}
	
	private static float clamp01(float value)
	{
		return Math.max(0F, Math.min(1F, value));
	}
}
