/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2.animation;

/**
 * 仿 VAPE DoubleAnimation 的双精度值动画。
 * 平滑插值当前值到目标值。
 */
public class DoubleAnimation
{
	private final double duration;
	private final double start;
	private final double end;
	
	private double current;
	private long startTime = -1;
	private long lastUpdate = -1;
	private boolean playing;
	
	public DoubleAnimation(double duration, double start, double end)
	{
		this.duration = duration;
		this.start = start;
		this.end = end;
		this.current = start;
	}
	
	/**
	 * 开始播放动画（从当前值到目标值）。
	 */
	public void play()
	{
		playing = true;
		startTime = System.nanoTime();
		lastUpdate = startTime;
	}
	
	/**
	 * 反向播放。
	 */
	public void reverse()
	{
		playing = true;
		startTime = System.nanoTime();
		lastUpdate = startTime;
	}
	
	/**
	 * 每帧更新并返回当前值。
	 */
	public double update()
	{
		if(!playing)
			return current;
		
		long now = System.nanoTime();
		if(lastUpdate == -1)
			lastUpdate = now;
		long delta = now - lastUpdate;
		lastUpdate = now;
		
		double progress = (now - startTime) / (duration * 1_000_000_000.0);
		progress = Math.min(1.0, Math.max(0.0, progress));
		
		// easeOutCubic
		double eased = 1 - Math.pow(1 - progress, 3);
		current = start + (end - start) * eased;
		
		if(progress >= 1.0)
			playing = false;
		
		return current;
	}
	
	/**
	 * 获取当前插值（0-1）。
	 */
	public double getInterpolatedValue()
	{
		return (current - start) / (end - start);
	}
	
	/**
	 * 是否播放中。
	 */
	public boolean isPlaying()
	{
		return playing;
	}
	
	/**
	 * 是否已到终点。
	 */
	public boolean hasReachedEnd()
	{
		return !playing && current == end;
	}
	
	public double getCurrentValue()
	{
		return current;
	}
}
