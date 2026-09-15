/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.anim;

import java.util.function.LongSupplier;

/**
 * 带缓动曲线的标量补间，语义对齐既有的
 * {@code net.wurstclient.clickgui2.supersoft.UiTween}。
 *
 * <p>
 * 为什么不是直接用 {@link Easings} 配 {@code UiTween}：{@code UiTween} 只在
 * linear 与 smoothstep 之间二选一，选不了曲线。这里把曲线做成参数。
 *
 * <p>
 * <b>刻意没有去改 {@code UiTween}</b>：它被 clickgui2 的二十多个组件共用，
 * 换掉内部的插值会波及整个点击界面的观感。两者并存，新代码用本类。
 *
 * <p>
 * 三条与参考 {@code Animation} 不同的地方，都是有意为之：
 *
 * <ol>
 * <li><b>零时长在同一次 update 内到位</b>。参考的 {@code getProgress()} 是
 * {@code (now - startTime) / (float) duration}，{@code duration = 0} 时除以零
 * 得到 {@code Infinity}；参考靠一条显式的 {@code if (duration == 0L)} 分支与
 * NaN 兜底绕开了它，所以那属于「潜在不可达」而不是现网 bug。本类把时长夹到
 * 1ms 以免除零，另外用 {@code instant} 标志让配置时长 &le; 0 的补间在同一次
 * {@code update} 里直接到位——只夹到 1ms 是不够的，那会白白多滞后一帧
 * （这个滞后是本类自己的单测发现的）。</li>
 * <li><b>进度钳制在 {@code [0,1]}</b>。参考的 {@code getProgress()} 不钳制，
 * 靠 {@code finished} 分支提前返回才没有越界。</li>
 * <li><b>终点收口</b>：{@code elapsed >= 1} 时直接写目标值。这条对
 * {@link Easings#DYNAMIC_ISLAND} 是必需的——它 {@code f(1) ≈ 1.004}，
 * 不收口就永远差 0.4%；对 {@link Easings#SIGMOID} 这类不满足
 * {@code f(0)=0} 的曲线也让终态可预期。</li>
 * </ol>
 *
 * <p>
 * 时钟可注入（包私有构造），因为补间行为必须能确定性地测——{@code UiTween}
 * 直接读 {@code System.nanoTime()}，所以它至今没有测试。
 */
public final class EasedTween
{
	private final long durationNanos;
	private final Easings easing;
	private final LongSupplier clock;

	/**
	 * 配置的时长 &le; 0 时为 true：这种补间必须<b>在同一次 update 里</b>直接
	 * 到位。参考 {@code Animation} 有一条显式的 {@code if (duration == 0L)}
	 * 分支做这件事；只把时长夹到 1ms 是不够的——1ms 也要等下一帧才走完，
	 * 会白白多滞后一帧。
	 */
	private final boolean instant;

	private float value;
	private float startValue;
	private float target;
	private long startNanos;

	public EasedTween(float initialValue, int durationMillis, Easings easing)
	{
		this(initialValue, durationMillis, easing, System::nanoTime);
	}

	EasedTween(float initialValue, int durationMillis, Easings easing,
		LongSupplier clock)
	{
		if(easing == null)
			throw new IllegalArgumentException("easing must not be null");
		if(clock == null)
			throw new IllegalArgumentException("clock must not be null");

		value = initialValue;
		startValue = initialValue;
		target = initialValue;
		instant = durationMillis <= 0;
		durationNanos = Math.max(1, durationMillis) * 1_000_000L;
		this.easing = easing;
		this.clock = clock;
	}

	/**
	 * 每帧推进一步并返回当前值。目标不变时是幂等的，可以无条件每帧调用。
	 */
	public float update(float newTarget)
	{
		long now = clock.getAsLong();

		advance(now);
		if(Float.compare(target, newTarget) != 0)
		{
			startValue = value;
			target = newTarget;
			startNanos = now;
		}
		advance(now);

		return value;
	}

	private void advance(long now)
	{
		if(Float.compare(value, target) == 0)
			return;

		float elapsed = instant ? 1F
			: Math.min(1F, (now - startNanos) / (float)durationNanos);
		float eased = easing.apply(elapsed);

		value = startValue + (target - startValue) * eased;

		// 曲线允许在中间冲出 [0,1]（EASE_IN_BACK / EASE_OUT_ELASTIC），所以
		// 不在中途钳制；只在收尾时收口，并且无论如何都不让 NaN/Infinity 进入
		// 状态（参考 Animation.java:43 有同样的兜底）。
		if(elapsed >= 1F || !Float.isFinite(value))
			value = target;
	}

	public float get()
	{
		return value;
	}

	public float getTarget()
	{
		return target;
	}

	public Easings getEasing()
	{
		return easing;
	}

	/** 是否已经在目标值上（不需要再重绘）。 */
	public boolean isSettled()
	{
		return Float.compare(value, target) == 0;
	}

	/** 立刻跳到给定值，并把它当作新的当前位置与目标。 */
	public void snap(float value)
	{
		this.value = value;
		startValue = value;
		target = value;
		startNanos = 0;
	}
}
