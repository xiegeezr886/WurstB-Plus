/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

/**
 * BowAimbot 的箭弹道解算（纯逻辑，不引用任何 Minecraft 类型）。
 *
 * <p>
 * 原来的实现用的是经典的无阻力抛物线公式（{@code g = 0.006} 配归一化拉弓力度，
 * 等价于「初速 3.0 格/tick、重力 0.05、没有空气阻力」）；参考项目 OpenEpsilon 的
 * {@code AimBot.getBowAim}（{@code _oe_ref/src/main/kotlin/studio/coni/epsilon/
 * module/combat/AimBot.kt:105-108}）用的是同一个公式。两边的模型都漏掉了 1.20.1
 * 箭真实运动的三个事实：
 *
 * <p>
 * 1. 每 tick 先用当前速度位移，然后速度乘 0.99，再减 0.05（1.20.1 的
 * {@code AbstractArrow#tick}，反编译里就是 {@code 0.99f} 与 {@code 0.05f}，
 * 而且是在 {@code setPos} 之前对速度做的）。无阻力公式因此有系统偏差：静立射
 * 20 格的误差约 0.21 格，30 格约 0.28 格（全部偏高）。
 *
 * <p>
 * 2. 箭从「眼睛高度 - 0.1」处生成（{@code AbstractArrow} 的 LivingEntity 构造器
 * 用 {@code getEyeY() - 0.1F}）。
 *
 * <p>
 * 3. 箭会继承射手当 tick 的速度：{@code Projectile#shootFromRotation} 末尾是
 * {@code setDeltaMovement(getDeltaMovement().add(shooter.getDeltaMovement()))}，
 * 其中竖直分量只在射手不在地面时才加。这一项比阻力误差大得多，而且正好是原实现
 * 完全没考虑的：以 20 格、走路速度 0.2158 格/tick 为例，箭在约 6.9 tick 的飞行里
 * 会横向漂 1.45 格，远超目标 0.6 格的宽度。
 *
 * <p>
 * 这里按上面三条做数值解算：初速固定为 speed，水平速度的朝向必须指向目标（水平
 * 分量按同一个 0.99 等比衰减，所以箭的水平轨迹一定是直线），于是每个候选俯仰角都
 * 唯一确定了偏航角与竖直初速，再「从正下方往正上方」扫描，取第一个穿越目标高度的
 * 解（最平直的那条弹道）。
 */
public enum BowAimbotTrajectory
{
	;

	/** 箭每 tick 的速度衰减（{@code AbstractArrow#tick} 里的 0.99）。 */
	private static final double DRAG = 0.99;

	/** 箭每 tick 的重力（{@code AbstractArrow#tick} 里的 0.05）。 */
	private static final double GRAVITY = 0.05;

	/**
	 * 水平速度按 0.99 等比衰减，最多只能飞 1/(1-0.99) = 100 倍水平初速。这里取
	 * 略小的值当提前退出的门槛，飞不到就直接判为无解。
	 */
	private static final double HORIZONTAL_REACH = 99.9;

	/** 单次解算里最多模拟的 tick 数。 */
	private static final int MAX_TICKS = 400;

	/** 俯仰角扫描范围与步长（度），留出余量避开正上/正下方的退化。 */
	private static final double MAX_PITCH = 89.9;
	private static final double SCAN_STEP = 0.25;

	/** 找到穿越之后二分的次数。 */
	private static final int REFINE_STEPS = 60;

	/** 判断水平距离是否退化（目标几乎在正上/正下方）的阈值。 */
	private static final double MIN_DISTANCE = 1.0E-6;

	/**
	 * 解算结果：偏航角与俯仰角，都是 Minecraft 的角度制（俯仰角为负表示朝上）。
	 */
	public record Aim(double yaw, double pitch)
	{
	}

	/**
	 * 解算「初速为 speed 的箭要打中相对生成点 (dx, dy, dz) 的目标，该往哪看」。
	 *
	 * @param speed
	 *            箭的初速（格/tick）：弓是 {@code 拉弓力度 * 3.0}，弩是 3.15
	 * @param dx
	 *            目标相对箭生成点的 x 偏移
	 * @param dy
	 *            目标相对箭生成点的 y 偏移
	 * @param dz
	 *            目标相对箭生成点的 z 偏移
	 * @param shooterVx
	 *            射手当前 x 速度（会被箭继承）
	 * @param shooterVy
	 *            射手当前 y 速度（只在射手不在地面时会被箭继承）
	 * @param shooterVz
	 *            射手当前 z 速度（会被箭继承）
	 * @param shooterOnGround
	 *            射手是否在地面上
	 * @return 需要的角度。目标超出射程时返回「能飞到最高点」的那个角度（尽力而
	 *         为，比原来的直接瞄准更接近目标）；只有几何退化（水平距离为 0、初速
	 *         非正）时才返回 null
	 */
	public static Aim solve(double speed, double dx, double dy, double dz,
		double shooterVx, double shooterVy, double shooterVz,
		boolean shooterOnGround)
	{
		double distance = Math.sqrt(dx * dx + dz * dz);
		if(!(speed > 0) || distance < MIN_DISTANCE)
			return null;

		double ux = dx / distance;
		double uz = dz / distance;
		double vy = shooterOnGround ? 0 : shooterVy;

		Aim best = null;
		double bestError = Double.NEGATIVE_INFINITY;
		double previousError = errorAt(MAX_PITCH, speed, ux, uz, distance, dy,
			shooterVx, vy, shooterVz);

		for(double pitch = MAX_PITCH - SCAN_STEP; pitch >= -MAX_PITCH;
			pitch -= SCAN_STEP)
		{
			double error = errorAt(pitch, speed, ux, uz, distance, dy,
				shooterVx, vy, shooterVz);

			// 从俯（正角）往仰扫，第一个「低于目标 -> 不低于目标」的穿越就是最
			// 平直的那个解；另一个解是高抛弹道，不是瞄准想要的
			if(previousError < 0 && error >= 0)
			{
				double solution = refine(pitch, pitch + SCAN_STEP, speed, ux, uz,
					distance, dy, shooterVx, vy, shooterVz);
				return new Aim(
					yawAt(solution, speed, ux, uz, shooterVx, shooterVz),
					solution);
			}

			// 记下至今「到达高度最高」的角度，射程不够时用它尽力而为。
			// 误差为负无穷表示这个角度根本解不出来（没有合法的偏航角或用不上
			// 力），一律跳过，免得返回一个 yaw 是 NaN 的结果
			if(!Double.isInfinite(error)
				&& (best == null || error > bestError))
			{
				bestError = error;
				best = new Aim(
					yawAt(pitch, speed, ux, uz, shooterVx, shooterVz), pitch);
			}

			previousError = error;
		}

		return best;
	}

	/**
	 * 按 1.20.1 的箭运动逐 tick 模拟，返回箭的水平距离达到 distance 时的高度。
	 *
	 * <p>
	 * 模拟顺序与原版一致：先按当前速度位移，再 {@code *= 0.99}，再
	 * {@code -= 0.05}。水平距离是在一 tick 的位移里线性插值出来的。
	 *
	 * @return 到达该水平距离时的高度；水平速度不足以飞到那里时返回 NaN
	 */
	public static double heightAtDistance(double horizontalSpeed,
		double verticalSpeed, double distance)
	{
		if(horizontalSpeed * HORIZONTAL_REACH < distance)
			return Double.NaN;

		double x = 0;
		double y = 0;
		double vx = horizontalSpeed;
		double vy = verticalSpeed;

		for(int tick = 0; tick < MAX_TICKS; tick++)
		{
			double previousX = x;
			double previousY = y;
			x += vx;
			y += vy;

			if(x >= distance)
			{
				if(x == previousX)
					return y;

				return previousY
					+ (y - previousY) * (distance - previousX) / (x - previousX);
			}

			vx *= DRAG;
			vy = vy * DRAG - GRAVITY;
		}

		return Double.NaN;
	}

	/**
	 * 二分细化：above 一侧的误差不小于 0，below 一侧的误差小于 0。
	 */
	private static double refine(double above, double below, double speed,
		double ux, double uz, double distance, double dy, double vx, double vy,
		double vz)
	{
		for(int i = 0; i < REFINE_STEPS; i++)
		{
			double middle = (above + below) / 2;

			if(errorAt(middle, speed, ux, uz, distance, dy, vx, vy, vz) < 0)
				below = middle;
			else
				above = middle;
		}

		return (above + below) / 2;
	}

	/**
	 * 给定俯仰角时「箭到达目标水平距离处的高度」与目标高度的差。
	 *
	 * @return 差值；该俯仰角下无解时返回负无穷（这样扫描时天然排到最后）
	 */
	private static double errorAt(double pitch, double speed, double ux,
		double uz, double distance, double dy, double vx, double vy, double vz)
	{
		double horizontal = horizontalSpeed(pitch, speed, ux, uz, vx, vz);
		if(Double.isNaN(horizontal))
			return Double.NEGATIVE_INFINITY;

		double radians = Math.toRadians(pitch);
		double vertical = -speed * Math.sin(radians) + vy;
		double height = heightAtDistance(horizontal, vertical, distance);

		return Double.isNaN(height) ? Double.NEGATIVE_INFINITY : height - dy;
	}

	/**
	 * 让箭的水平速度正好指向目标所需的偏航角。
	 */
	private static double yawAt(double pitch, double speed, double ux, double uz,
		double vx, double vz)
	{
		double aimHorizontal = speed * Math.cos(Math.toRadians(pitch));
		double horizontal = horizontalSpeed(pitch, speed, ux, uz, vx, vz);

		double aimX = (horizontal * ux - vx) / aimHorizontal;
		double aimZ = (horizontal * uz - vz) / aimHorizontal;

		// 与本工程 RotationUtils.getNeededRotations 同一个约定：
		// yaw = atan2(dz, dx) - 90，等价于 atan2(-dx, dz)
		return Math.toDegrees(Math.atan2(-aimX, aimZ));
	}

	/**
	 * 在「箭的水平速度方向必须指向目标」的约束下解出箭的水平速度大小。
	 *
	 * <p>
	 * 设箭的水平初速为 {@code h * u}（u 是水平单位方向），射手的水平速度为 v，
	 * 瞄准方向的水平分量为 {@code speed * cos(pitch) * aim}，则
	 * {@code h * u = speed*cos(pitch)*aim + v}，对 aim 取模长 1 得到
	 * {@code h^2 - 2h(u·v) + |v|^2 - (speed*cos(pitch))^2 = 0}，取正根。
	 *
	 * @return 水平速度大小；该俯仰角下无解（判別式为负，或解出来的水平速度非正）
	 *         时返回 NaN
	 */
	private static double horizontalSpeed(double pitch, double speed,
		double ux, double uz, double vx, double vz)
	{
		double aimHorizontal = speed * Math.cos(Math.toRadians(pitch));
		if(aimHorizontal <= 0)
			return Double.NaN;

		double dot = ux * vx + uz * vz;
		double discriminant = dot * dot - (vx * vx + vz * vz)
			+ aimHorizontal * aimHorizontal;
		if(discriminant < 0)
			return Double.NaN;

		double horizontal = dot + Math.sqrt(discriminant);
		return horizontal > 0 ? horizontal : Double.NaN;
	}
}
