/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

/**
 * 原版投射物每 tick 的速度更新规则（纯计算：不含位置推进、不含碰撞）。
 *
 * <p>原版不是"每个 tick 重新算一遍抛物线"，而是逐步迭代，且<b>位置的推进用的是上一
 * tick 末的速度</b>，之后才更新速度：
 *
 * <ul>
 * <li>箭：先用旧速度推进位置，再 {@code scale(0.99)}，再 {@code y -= 0.05}
 * ——1.20.2 真源 {@code world/entity/projectile/AbstractArrow.java:240-257}
 * （{@code f = 0.99F}、{@code f1 = 0.05F}，顺序是 {@code setDeltaMovement(scale(f))}
 * 之后 {@code setDeltaMovement(y - 0.05F)}，最后 {@code setPos}）。</li>
 * <li>投掷物（雪球/鸡蛋/末影珍珠/药水/经验瓶）：同样是先 {@code scale(0.99)}
 * 再减 {@code getGravity()}——{@code ThrowableProjectile.java:82-91}。</li>
 * <li>鱼漂：顺序相反，先减重力、再推进位置、最后 {@code scale(0.92)}
 * ——{@code FishingHook.java:223-233}（重力 {@code 0.03}）。</li>
 * </ul>
 *
 * <p>所以完整的每 tick 流程是：
 * {@code p += v;}（用旧 {@code v}）{@code v = dragAndGravity(v)}。
 * 调用方负责第一步，本类负责第二步（鱼漂用 {@link #gravity} + {@link #drag} 两步）。
 */
public enum ProjectilePhysics
{
	;

	/** 箭与投掷物的空气阻力（{@code AbstractArrow.java:240}、{@code ThrowableProjectile.java:82}）。 */
	public static final double DRAG = 0.99;

	/** 鱼漂的空气阻力（{@code FishingHook.java:232-233}）。 */
	public static final double FISHING_DRAG = 0.92;

	/**
	 * 每个游戏 tick 在轨迹上细分出的点数。细分点全部落在"这一 tick 的直线位移"上，
	 * 所以不会改变轨迹本身，只让折线更平滑、碰撞检测更细。
	 */
	public static final int SUB_STEPS = 10;

	/** 箭/投掷物：先乘阻力，再减重力（同一个 tick 内）。 */
	public static Velocity dragAndGravity(Velocity v, double drag, double gravity)
	{
		return new Velocity(v.x() * drag, v.y() * drag - gravity, v.z() * drag);
	}

	/** 鱼漂的第二步：只减重力（位移推进之前）。 */
	public static Velocity gravity(Velocity v, double gravity)
	{
		return new Velocity(v.x(), v.y() - gravity, v.z());
	}

	/** 鱼漂的最后一步：只乘阻力。 */
	public static Velocity drag(Velocity v, double drag)
	{
		return new Velocity(v.x() * drag, v.y() * drag, v.z() * drag);
	}

	/** 三轴速度。用独立的 double 三元组而不是 {@code Vec3}，这样本类可以脱离 Minecraft 单测。 */
	public record Velocity(double x, double y, double z)
	{
	}
}
