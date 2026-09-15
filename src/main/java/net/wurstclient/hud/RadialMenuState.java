/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hud;

import java.util.List;

/**
 * 长按 Tab 唤出的功能圆盘的纯几何与状态：唤出判定、扇区命中、选中跟踪、
 * 开合动画进度。
 *
 * <p>
 * 交互参考 SoftBlack42/StarPie（Windows 上的圆盘手势启动器）：按住唤出、指针方向
 * 决定选中哪一片、**移回中心即取消**、松手确认。本工程原来只有一个方向键导航的
 * {@code TabGui}，没有圆盘。
 *
 * <p>
 * 这里刻意只放与 Minecraft 无关的数学与计时，因此扇区边界、死区、跨越 0° 的环绕、
 * 长按阈值这些最容易写错的地方都能被单测覆盖——绘制部分留给调用方。
 */
public final class RadialMenuState
{
	/** 长按多少毫秒才唤出圆盘；短按仍然走原来的 TabGUI。 */
	public static final long HOLD_DELAY_MS = 220L;
	
	/** 开合动画时长。 */
	public static final long OPEN_MS = 150L;
	public static final long CLOSE_MS = 110L;
	
	/** 默认项数上限：项太多扇区就细到点不中了。 */
	public static final int MAX_SLICES = 12;
	public static final int MIN_SLICES = 2;
	
	/**
	 * 死区半径占外半径的比例。指针落在这里面视为「没有指向任何扇区」，
	 * 松手即取消——这是圆盘能反悔的关键。
	 */
	public static final float DEAD_ZONE_RATIO = 0.34F;
	
	/** 扇区之间留的角度缝隙（度），让圆盘看起来是分片的。 */
	public static final float SLICE_GAP_DEGREES = 2.0F;
	
	private final List<String> items;
	
	private boolean open;
	/**
	 * 是否正在播放关闭动画。没有这个标志就无法区分"刚关闭"和"从没打开过"——
	 * 两者的 elapsed 都是 0，会把没开过的圆盘报成完全展开。
	 */
	private boolean closing;
	private long openStartedAt;
	private long pressStartedAt = Long.MIN_VALUE;
	private boolean holding;
	private int selected = -1;
	
	public RadialMenuState(List<String> items)
	{
		this.items = List.copyOf(items);
	}
	
	/**
	 * 按下 Tab：开始计时。短按由调用方按原样处理，长按由 {@link #update} 判定。
	 */
	public void press(long nowMs)
	{
		if(holding)
			return;
		
		holding = true;
		pressStartedAt = nowMs;
	}
	
	/**
	 * 每帧推进。返回 true 表示这一帧刚刚唤出圆盘，调用方可以据此播音效。
	 *
	 * @param nowMs    当前毫秒
	 * @param held     按键是否仍然按住
	 * @param pointerX 指针相对圆心的横向偏移
	 * @param pointerY 指针相对圆心的纵向偏移
	 * @param radius   圆盘外半径（像素）
	 */
	public boolean update(long nowMs, boolean held, double pointerX,
		double pointerY, float radius)
	{
		if(!held)
		{
			holding = false;
			pressStartedAt = Long.MIN_VALUE;
			selected = -1;
			
			if(open)
				close(nowMs);
			
			return false;
		}
		
		if(pressStartedAt == Long.MIN_VALUE)
			pressStartedAt = nowMs;
		
		boolean justOpened = false;
		
		if(!open && nowMs - pressStartedAt >= HOLD_DELAY_MS)
		{
			open = true;
			closing = false;
			openStartedAt = nowMs;
			justOpened = true;
		}
		
		if(open)
			selected = sliceAt(pointerX, pointerY, radius);
		
		return justOpened;
	}
	
	/** 松手时选中的项下标；没有选中（未开、在死区、已取消）返回 -1。 */
	public int commit()
	{
		int result = open ? selected : -1;
		open = false;
		closing = false;
		selected = -1;
		holding = false;
		pressStartedAt = Long.MIN_VALUE;
		return result;
	}
	
	public void close(long nowMs)
	{
		if(!open)
			return;
		
		open = false;
		closing = true;
		selected = -1;
		openStartedAt = nowMs;
	}
	
	/**
	 * 开合进度：0 完全收起，1 完全展开。关闭时从当前值往回走，不会跳变。
	 */
	public float progress(long nowMs)
	{
		if(open)
		{
			long duration = OPEN_MS;
			
			if(duration <= 0L)
				return 1F;
			
			float elapsed = nowMs - openStartedAt;
			
			if(elapsed >= duration)
				return 1F;
			
			if(elapsed <= 0L)
				return 0F;
			
			return easeOutCubic(elapsed / (float)duration);
		}
		
		// 从没打开过（或已经收完）就是完全收起
		if(!closing)
			return 0F;
		
		long duration = CLOSE_MS;
		
		if(duration <= 0L)
			return 0F;
		
		float elapsed = nowMs - openStartedAt;
		
		if(elapsed >= duration)
			return 0F;
		
		if(elapsed <= 0L)
			return 1F;
		
		return 1F - easeOutCubic(elapsed / (float)duration);
	}
	
	public boolean isOpen()
	{
		return open;
	}
	
	public int selected()
	{
		return selected;
	}
	
	public List<String> items()
	{
		return items;
	}
	
	/** 实际可用的扇区数量：夹在上下限之间。 */
	public int sliceCount()
	{
		return Math.max(MIN_SLICES, Math.min(MAX_SLICES, items.size()));
	}
	
	/**
	 * 指针指向第几片。落在死区内、超出外半径、或没有项时返回 -1。
	 *
	 * <p>
	 * 角度约定：**0° 指向正上方，顺时针增加**，第 0 片以正上方为中心，其余等分。
	 * 扇区之间留 {@link #SLICE_GAP_DEGREES} 的缝隙，落在缝隙里算「没选中」而不是
	 * 硬塞给相邻扇区——这样贴着边界松手不会误触。
	 */
	public int sliceAt(double pointerX, double pointerY, float radius)
	{
		int count = sliceCount();
		
		if(items.isEmpty() || radius <= 0F)
			return -1;
		
		double distance = Math.sqrt(pointerX * pointerX + pointerY * pointerY);
		
		if(distance < radius * DEAD_ZONE_RATIO || distance > radius)
			return -1;
		
		// atan2(x, -y)：正上方为 0°，顺时针为正
		double angle =
			wrap360(Math.toDegrees(Math.atan2(pointerX, -pointerY)));
		double step = 360D / count;
		// 平移半格，使第 0 片以 0° 为中心
		double offset = wrap360(angle + step / 2D);
		int index = (int)(offset / step);
		
		if(index < 0 || index >= count)
			return -1;
		
		double within = offset - index * step;
		
		if(Math.abs(within - step / 2D) > step / 2D - SLICE_GAP_DEGREES / 2D)
			return -1;
		
		return index < items.size() ? index : -1;
	}
	
	/** 第 index 片中心角度（度）。 */
	public float sliceCentre(int index)
	{
		return (float)(index * 360D / sliceCount());
	}
	
	/** 第 index 片起始角度（度）。 */
	public float sliceStart(int index)
	{
		return sliceCentre(index) - 180F / sliceCount();
	}
	
	/** 第 index 片扫过的角度，已扣掉缝隙。 */
	public float sliceSweep()
	{
		return 360F / sliceCount() - SLICE_GAP_DEGREES;
	}
	
	static double wrap360(double degrees)
	{
		double wrapped = degrees % 360D;
		return wrapped < 0D ? wrapped + 360D : wrapped;
	}
	
	static double wrap180(double degrees)
	{
		double wrapped = wrap360(degrees);
		return wrapped > 180D ? wrapped - 360D : wrapped;
	}
	
	private static float easeOutCubic(float t)
	{
		float clamped = Math.max(0F, Math.min(1F, t));
		float inverse = 1F - clamped;
		return 1F - inverse * inverse * inverse;
	}
}
