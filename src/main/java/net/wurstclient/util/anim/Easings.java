/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util.anim;

/**
 * OpenOpal {@code utility/render/animation/Easing} 的曲线集合（28 条）。
 *
 * <p>
 * 纯函数，不含 {@code net.minecraft.} 依赖：参考用的是 Yarn 的
 * {@code MathHelper.cos/sin}，本工程换成 {@code Math.cos/sin}——{@code MathHelper}
 * 那两个方法本来就是 {@code (float)Math.xxx} 的薄封装，数值完全一致。
 *
 * <p>
 * <b>本类与参考有两处实质差异，都是参考自身的错误</b>，都有可复算的证据：
 *
 * <h2>1. {@code EASE_IN_OUT_SINE} 在参考里和 {@code EASE_IN_SINE} 是同一个函数</h2>
 * 参考 {@code Easing.java:23} 与 {@code :25}：
 * <pre>
 * EASE_IN_SINE     (x -&gt; 1 - MathHelper.cos((float) (x * Math.PI * 0.5D)))
 * EASE_IN_OUT_SINE (x -&gt; 1 - MathHelper.cos((float) (Math.PI * x * 0.5D)))
 * </pre>
 * 两个表达式只差乘法的书写顺序，而浮点乘法可交换，所以逐位相同。证据：
 * 取 {@code x = 0.25}，两者都给出 {@code 1 - cos(0.3927) = 0.07612}；而真正的
 * ease-in-out 曲线必须满足 {@code f(0.5) = 0.5}，参考那一条给出
 * {@code 1 - cos(0.7854) = 0.29289}。这里改用标准式
 * {@code (1 - cos(πx)) / 2}。
 *
 * <h2>2. {@code EASE_IN_OUT_CIRC} 在参考里是不连续且会冲过 1 的</h2>
 * 参考 {@code Easing.java:31} 的第二段写的是 {@code sqrt(1 - 4(x-1)x)}。
 * 标准式的第二段应该是 {@code sqrt(1 - (-2x+2)²)}，而
 * {@code (-2x+2)² = 4x² - 8x + 4}，参考写的 {@code 4(x-1)x = 4x² - 4x}，
 * 两者只在 {@code x = 1} 处相等。后果：
 * <pre>
 * x -&gt; 0.5⁻ （第一段）  ：(1 - sqrt(0)) * 0.5            = 0.5
 * x  = 0.5  （第二段）  ：(sqrt(1 - 4(-0.5)(0.5)) + 1)/2 = 1.20711
 * </pre>
 * 也就是在 {@code x = 0.5} 处直接跳变 0.5 → 1.207，并且整段都大于 1。
 * 这里改用标准式。
 *
 * <p>
 * 除这两条以外全部照抄。参考里另有两条曲线「形态可疑但不是错误」，照搬并在
 * 各自的注释里写明：{@link #SIGMOID} 不满足 {@code f(0)=0}，
 * {@link #DYNAMIC_ISLAND} 在 {@code x=1} 处约为 {@code 1.004} 而不是 1。
 */
public enum Easings implements Curve
{
	// ---------------------------------------------------------------- 基础

	LINEAR(x -> x),

	DECELERATE(x -> 1 - (x - 1) * (x - 1)),

	SMOOTH_STEP(x -> x * x * (3 - 2 * x)),

	// ---------------------------------------------------------------- quad

	EASE_IN_QUAD(x -> x * x),

	EASE_OUT_QUAD(x -> x * (2 - x)),

	EASE_IN_OUT_QUAD(x -> x < 0.5F ? 2 * x * x : -1 + (4 - 2 * x) * x),

	// --------------------------------------------------------------- cubic

	EASE_IN_CUBIC(x -> x * x * x),

	EASE_OUT_CUBIC(x -> {
		float d = x - 1;
		return d * d * d + 1;
	}),

	EASE_IN_OUT_CUBIC(x -> x < 0.5F ? 4 * x * x * x
		: (x - 1) * (2 * x - 2) * (2 * x - 2) + 1),

	// --------------------------------------------------------------- quart

	EASE_IN_QUART(x -> x * x * x * x),

	EASE_OUT_QUART(x -> {
		float d = x - 1;
		return 1 - d * d * d * d;
	}),

	EASE_IN_OUT_QUART(x -> {
		if(x < 0.5F)
			return 8 * x * x * x * x;
		float d = x - 1;
		return 1 - 8 * d * d * d * d;
	}),

	// --------------------------------------------------------------- quint

	EASE_IN_QUINT(x -> x * x * x * x * x),

	EASE_OUT_QUINT(x -> {
		float d = x - 1;
		return 1 + d * d * d * d * d;
	}),

	EASE_IN_OUT_QUINT(x -> {
		if(x < 0.5F)
			return 16 * x * x * x * x * x;
		float d = x - 1;
		return 1 + 16 * d * d * d * d * d;
	}),

	// ---------------------------------------------------------------- sine

	EASE_IN_SINE(x -> 1 - (float)Math.cos(x * Math.PI * 0.5)),

	EASE_OUT_SINE(x -> (float)Math.sin(x * Math.PI * 0.5)),

	/** 见类注释第 1 条：参考把这一条写成了 {@link #EASE_IN_SINE}。 */
	EASE_IN_OUT_SINE(x -> (float)((1 - Math.cos(Math.PI * x)) / 2)),

	// ---------------------------------------------------------------- expo

	EASE_IN_EXPO(x -> x == 0 ? 0 : (float)Math.pow(2, 10 * x - 10)),

	EASE_OUT_EXPO(x -> x == 1 ? 1 : 1 - (float)Math.pow(2, -10 * x)),

	EASE_IN_OUT_EXPO(x -> {
		if(x == 0)
			return 0;
		if(x == 1)
			return 1;
		if(x < 0.5F)
			return (float)Math.pow(2, 20 * x - 10) * 0.5F;
		return (2 - (float)Math.pow(2, -20 * x + 10)) * 0.5F;
	}),

	// ---------------------------------------------------------------- circ

	EASE_IN_CIRC(x -> 1 - (float)Math.sqrt(1 - x * x)),

	EASE_OUT_CIRC(x -> {
		float d = x - 1;
		return (float)Math.sqrt(1 - d * d);
	}),

	/** 见类注释第 2 条：参考在这一条上会把结果冲过 1，且 x=0.5 处跳变。 */
	EASE_IN_OUT_CIRC(x -> {
		if(x < 0.5F)
			return (1 - (float)Math.sqrt(1 - 4 * x * x)) * 0.5F;
		float d = 2 - 2 * x;
		return ((float)Math.sqrt(1 - d * d) + 1) * 0.5F;
	}),

	// --------------------------------------------------------------- 其它

	/**
	 * <b>照搬参考，但它不是归一化曲线</b>：{@code SIGMOID(0) = 0.5}、
	 * {@code SIGMOID(1) ≈ 0.7311}，不满足 {@code f(0)=0}、{@code f(1)=1}。
	 * 形态确实是 S 形，所以不像笔误，但如果直接拿它当补间曲线，动画会「起步
	 * 就走到半程」。用时务必知情。
	 */
	SIGMOID(x -> 1 / (1 + (float)Math.exp(-x))),

	/** 会冲出 1（弹性效果），{@code EASE_OUT_ELASTIC(0.5) ≈ 1.0078}。 */
	EASE_OUT_ELASTIC(x -> {
		if(x == 0)
			return 0;
		if(x == 1)
			return 1;
		return (float)(Math.pow(2, -10 * x)
			* Math.sin((x * 10 - 0.75) * ((2 * Math.PI) / 3)) * 0.5 + 1);
	}),

	/** 会跌到 0 以下（回拉效果），{@code EASE_IN_BACK(0.5) ≈ -0.0877}。 */
	EASE_IN_BACK(x -> 2.70158F * x * x * x - 1.70158F * x * x),

	/** 照搬参考：{@code DYNAMIC_ISLAND(1) ≈ 1.0040}，收尾时差 0.4%。 */
	DYNAMIC_ISLAND(x -> (float)(1 - Math.cos(x * Math.PI
		* (0.2 + 2.5 * Math.pow(x, 3))) * Math.exp(-x * 5)));

	private final Curve curve;

	Easings(Curve curve)
	{
		this.curve = curve;
	}

	@Override
	public float apply(float x)
	{
		return curve.apply(x);
	}
}
