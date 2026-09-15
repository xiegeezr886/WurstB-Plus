/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

/**
 * 把要发给服务器的朝向吸附到"鼠标灵敏度栅格"上。
 *
 * <p>原版客户端的每一度旋转都只能由鼠标像素位移产生：{@code MouseHandler} 先算
 * {@code multiplier = (sensitivity * 0.6 + 0.2)^3 * 8}，再按 {@code 像素数 * multiplier * 0.15}
 * 改变视角。所以真实玩家发出去的朝向永远落在以"上一次朝向"为锚点、步长
 * {@code multiplier * 0.15} 的整数倍上。
 *
 * <p>作弊客户端直接写朝向时不会有这个性质（可以停在任意小数角度）。把朝向吸附回栅格后，
 * 服务端看到的旋转序列与真人鼠标输入不可区分（这是 OpenOpal 的
 * {@code RotationUtility.patchConstantRotation} 所做的事）。
 *
 * <p>本类刻意不引用任何 Minecraft 类型，纯函数，可直接单元测试。
 */
public enum RotationGcdPolicy
{
	;

	/** 原版 0..1 灵敏度对应的每像素度数（{@code (s*0.6+0.2)^3 * 8 * 0.15}）。 */
	public static double gridStep(double mouseSensitivity)
	{
		double sensitivity = clampSensitivity(mouseSensitivity);
		double multiplier = sensitivity * 0.6 + 0.2;
		multiplier = multiplier * multiplier * multiplier * 8.0;
		return multiplier * 0.15;
	}

	/**
	 * 把 {@code value} 吸附到"以 {@code previous} 为锚点、步长 {@code gridStep} 的整数倍"上。
	 * 栅格以当前朝向为锚点，因此连续两帧之间只差整数个鼠标像素。
	 *
	 * <p>步长请用 {@link #gridStep(double)} 从鼠标灵敏度算出来。步长非正或非有限
	 * （例如灵敏度为 0 时理论上无法吸附）时原样返回。
	 */
	public static float patch(float value, float previous, double gridStep)
	{
		if(!Float.isFinite(value))
			return previous;

		if(!Double.isFinite(gridStep) || gridStep <= 0)
			return value;

		double offset = (value - previous) / gridStep;
		double snapped = Math.round(offset);
		return (float)(previous + snapped * gridStep);
	}

	public static Rotation patch(Rotation value, Rotation previous,
		double gridStep)
	{
		if(value == null)
			return previous;
		if(previous == null)
			return value;

		return new Rotation(patch(value.yaw(), previous.yaw(), gridStep),
			patch(value.pitch(), previous.pitch(), gridStep));
	}

	/** 便捷重载：直接给鼠标灵敏度，内部换算成步长。 */
	public static Rotation patch(Rotation value, Rotation previous,
		float mouseSensitivity)
	{
		return patch(value, previous, gridStep(mouseSensitivity));
	}

	/** 原版滑块范围是 0..1；越界或非有限值按边界处理。 */
	private static double clampSensitivity(double mouseSensitivity)
	{
		if(!Double.isFinite(mouseSensitivity))
			return 0;

		return Math.max(0, Math.min(1, mouseSensitivity));
	}
}
