/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

/**
 * 视频背景的时间规则：这一帧该不该显示、挂钟和解码位置什么时候算脱节、循环怎么取模。
 *
 * <p>
 * 全是纯算术，所以这些规则可以脱离解码器单测（跑一遍真解码器才能验的东西不在
 * 这里）。解码线程与渲染线程共用同一套判断，不会各自算一份、各算错一半。
 * </p>
 */
public final class VideoPacing
{
	/**
	 * 一秒最多往纹理上传多少帧。
	 *
	 * <p>
	 * 60fps 的源隔一帧丢一帧：每帧都要做一次 YUV→RGB 加一次全屏纹理上传，而标题
	 * 背景永远是被缩放着画的，多出来的帧肉眼看不出来，只是在白烧 CPU 和显存带宽。
	 * </p>
	 */
	public static final int CAP_FPS = 30;

	/**
	 * 挂钟与解码位置差到多少毫秒就算脱节，必须重新定位。
	 *
	 * <p>
	 * 实测会走到这里的有两种情况：①界面隐藏了一段时间（标题界面不在时渲染线程
	 * 不来取帧，解码线程最多提前三帧就停下），重新显示时挂钟已经过去几十秒；
	 * ②解码速度跟不上实时（1080p60 在纯 Java 解码器上很可能如此），落后会一直
	 * 累积。两种都不该把落下的帧一帧帧解完——那是几十秒的追赶，画面等于卡死。
	 * </p>
	 */
	public static final long SEEK_TOLERANCE_MS = 1_500;

	/** 「还没显示过任何一帧」的哨兵值，不能直接用 {@code 0}：第 0 毫秒也是合法时间。 */
	public static final long NO_FRAME = Long.MIN_VALUE;

	/** 一轮至少算这么久，免得元数据里时长为 0 的文件被无限次重开。 */
	public static final long MIN_PASS_MS = 100;

	private VideoPacing()
	{
	}

	/** 帧率上限换算成最小间隔（毫秒）。 */
	public static int minFrameIntervalMs(int capFps)
	{
		// 上限写成 0 或者负数时按「不限帧」处理（间隔 1 毫秒）：宁可多上传几帧，
		// 也不要因为一个写错的常量把壁纸变成 1fps 的幻灯片
		if(capFps < 1)
			return 1;

		return Math.max(1, 1000 / capFps);
	}

	/**
	 * 是否该把这一帧显示出来。
	 *
	 * <p>
	 * 距上一帧不足最小间隔就丢掉，这就是帧率上限的全部实现。判定时留 1 毫秒余量：
	 * 30fps 的源时间戳按毫秒取整后正好落在 33 上，而 {@code 1000/30} 的整数商也是
	 * 33，不留余量就会因为这点取整误差丢掉整整一半的帧。
	 * </p>
	 *
	 * @param lastShownMs
	 *            {@link #NO_FRAME} 表示还没显示过任何一帧，那时必定显示
	 */
	public static boolean shouldShowFrame(long dueMs, long lastShownMs,
		int minIntervalMs)
	{
		if(lastShownMs == NO_FRAME)
			return true;

		return dueMs - lastShownMs >= Math.max(1, minIntervalMs - 1);
	}

	/**
	 * 解码位置是不是落后挂钟太多了。
	 *
	 * <p>
	 * 只判「落后」：超前（这一帧还没到显示时刻）是正常的，睡过去就行，由调用方设
	 * 一个等待上限去兜住坏掉的时间戳。
	 * </p>
	 */
	public static boolean isBehind(long nowMs, long dueMs, long toleranceMs)
	{
		return nowMs - dueMs > toleranceMs;
	}

	/** 解码线程对刚解出来的一帧要做什么。 */
	public enum Action
	{
		/** 到点了，转换并交给渲染线程。 */
		SHOW,

		/** 还没到显示时刻，睡到那一时刻再重新判断。 */
		WAIT,

		/** 帧率上限以内的重复帧：解都解了，但不值得再转一次、上一次屏。 */
		DROP,

		/** 落后太多，按挂钟重新定位。 */
		RESYNC
	}

	/**
	 * 这一帧该做什么。
	 *
	 * <p>
	 * 这是整条播放规则唯一的分支点，纯粹由「现在几点、这一帧几点该显示、上一帧
	 * 什么时候显示的」决定，所以它可以单测；解码线程只负责按结果干活。
	 * </p>
	 *
	 * @param lastShownMs
	 *            {@link #NO_FRAME} 表示还没显示过任何一帧
	 * @param maxWaitMs
	 *            最多愿意为这一帧等多久。等不到就不等了：少数壁纸确实有长达数秒的
	 *            静止段（PTS 上就是一个大空档），那种情况等下去是对的，但坏掉的
	 *            时间戳（比如跳到几小时后）会让画面永久冻住，两者只能靠这个上限区分
	 */
	public static Action decide(long nowMs, long dueMs, long lastShownMs,
		int minIntervalMs, long toleranceMs, long maxWaitMs)
	{
		if(isBehind(nowMs, dueMs, toleranceMs))
			return Action.RESYNC;

		if(dueMs > nowMs)
			return dueMs - nowMs > maxWaitMs ? Action.RESYNC : Action.WAIT;

		if(!shouldShowFrame(dueMs, lastShownMs, minIntervalMs))
			return Action.DROP;

		return Action.SHOW;
	}

	/** 循环播放的位置：把已经过去的时间折进一轮里。时长非法时一律当作第 0 毫秒。 */
	public static long loopedTime(long elapsedMs, long durationMs)
	{
		if(durationMs <= 0)
			return 0;

		long position = elapsedMs % durationMs;
		return position < 0 ? position + durationMs : position;
	}

	/**
	 * 当前这一轮的起点（{@code elapsed - 位于轮内的位置}）。
	 *
	 * <p>
	 * 解码线程用它把每帧的 PTS 换算成「绝对显示时刻」：跳到第 n 轮的第 t 毫秒时，
	 * 那一轮的起点就是 {@code now - t}，于是帧的时刻仍然是一条连续的时间轴，
	 * 循环处不会出现时间倒流。
	 * </p>
	 */
	public static long loopBase(long elapsedMs, long durationMs)
	{
		return elapsedMs - loopedTime(elapsedMs, durationMs);
	}
}
