package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * {@link BowAimbotTrajectory} 的测试。
 *
 * <p>
 * 这里刻意**不复用**被测类的内部模拟，而是按 1.20.1 的原版代码另写一份：
 * {@code Projectile#shootFromRotation} 的方向与速度继承、
 * {@code AbstractArrow#tick} 的「先位移、再乘 0.99、再减 0.05」。解算结果必须在这
 * 份独立模拟里命中目标。
 */
final class BowAimbotTrajectoryTest
{
	@Test
	void anchorsTheIntegrationToTheGameRule()
	{
		// 初速 3、水平射：第 1 tick 只位移 (3, 0)
		assertEquals(0.0, BowAimbotTrajectory.heightAtDistance(3, 0, 3),
			1.0E-12);

		// 第 2 tick 的速度是 3*0.99 = 2.97，此时水平累计 (1+0.99)*3 = 5.97，
		// 竖直累计 (0+5)*1.99 - 5*2 = -0.05（先乘 0.99 再减 0.05）
		assertEquals(-0.05, BowAimbotTrajectory.heightAtDistance(3, 0, 5.97),
			1.0E-12);

		// 5.0 落在第 2 tick 的位移里，按水平方向线性插值：
		// (5-3)/(5.97-3) = 2/2.97，高度 0 + (-0.05) * 2/2.97
		assertEquals(-0.05 * 2 / 2.97,
			BowAimbotTrajectory.heightAtDistance(3, 0, 5), 1.0E-12);
	}

	@Test
	void hitsEveryStationaryTarget()
	{
		// 每一行的第一项是初速，后面是距离；高度另按 speedRows 一一对应。
		// 射程不够的组合不在表里（那种情况走「尽力而为」分支，另有测试）
		double[][] speedRows = {{3.0, 1, 2, 5, 10, 20, 30, 40, 50, 60},
			{3.15, 1, 2, 5, 10, 20, 30, 40, 50, 60}, {2.4, 1, 5, 10, 20, 30},
			{1.8, 1, 5, 10, 15, 20}, {0.9, 1, 3, 5, 10}};
		double[][] heightRows = {{-20, -10, -4, -2, 0, 2, 4, 10, 20},
			{-20, -10, -4, -2, 0, 2, 4, 10, 20}, {-10, -4, 0, 2, 10},
			{-10, -4, 0, 2, 10}, {-4, 0, 2}};
		int checked = 0;

		for(int row = 0; row < speedRows.length; row++)
			for(int i = 1; i < speedRows[row].length; i++)
				for(double height : heightRows[row])
				{
					assertHits(speedRows[row][0], speedRows[row][i], height, 0, 0,
						0, true);
					checked++;
				}

		assertEquals(224, checked);
	}

	@Test
	void hitsWhileTheShooterIsMoving()
	{
		// 走路 / 疾跑 / 反向 / 斜向的横向速度
		for(double vz : new double[]{0.2158, 0.2806, -0.2158, 0.1})
			for(double distance : new double[]{5, 10, 20, 30, 45})
				assertHits(3.0, distance, 0, 0, 0, vz, true);

		// 离地时的竖直速度：只有 shooterOnGround 为 false 时才会被箭继承
		for(double vy : new double[]{-1.0, -0.5, 0.3})
			for(double distance : new double[]{5, 10, 20, 30})
				assertHits(3.0, distance, -2, 0, vy, 0, false);
	}

	@Test
	void keepsTheYawOnTheTargetWhenStandingStill()
	{
		BowAimbotTrajectory.Aim aim = BowAimbotTrajectory.solve(3.0, 20, 0, 0,
			0, 0, 0, true);

		assertNotNull(aim);
		assertEquals(directYaw(20, 0), aim.yaw(), 1.0E-9);
	}

	@Test
	void addsTheMovementLeadWhileTheShooterIsMoving()
	{
		// 侧走 0.2158 格/tick：箭会往侧向漂，偏航角必须提前
		// atan(0.2158/3) ≈ 4.11 度
		BowAimbotTrajectory.Aim aim = BowAimbotTrajectory.solve(3.0, 20, 0, 0,
			0, 0, 0.2158, true);

		assertNotNull(aim);
		double lead = aim.yaw() - directYaw(20, 0);
		assertEquals(Math.toDegrees(Math.atan(0.2158 / 3.0)), Math.abs(lead),
			0.05);
		assertHits(3.0, 20, 0, 0, 0, 0.2158, true);
	}

	@Test
	void beatsTheOldDragFreeFormula()
	{
		// 原作的老公式：无阻力抛物线，g 取 0.006 并配归一化拉弓力度
		double distance = 30;
		double oldPitch =
			oldFormulaPitch(1.0, distance, 0);
		double oldError = hitError(3.0, directYaw(distance, 0), oldPitch, 0, 0, 0,
			true, distance, 0, 0);

		assertTrue(oldError > 0.2,
			"old formula should miss high by >0.2, was " + oldError);
		assertHits(3.0, distance, 0, 0, 0, 0, true);
	}

	@Test
	void fixesTheMissWhileWalking()
	{
		// 走路时箭会继承 0.2158 格/tick 的速度：老实现（偏航角正对目标 +
		// 老公式俯仰角）在 20 格处横向差 1.45 格，直接打不中 0.6 格宽的目标
		double distance = 20;
		double oldError = hitError(3.0, directYaw(distance, 0),
			oldFormulaPitch(1.0, distance, 0), 0, 0, 0.2158, true, distance, 0, 0);

		assertTrue(oldError > 1.0,
			"old aim should miss by >1 block, was " + oldError);
		assertHits(3.0, distance, 0, 0, 0, 0.2158, true);
	}

	@Test
	void prefersTheFlatTrajectory()
	{
		// 20 格、同高度：平直解在 -2.85 度附近，高抛解在 -80 度附近
		BowAimbotTrajectory.Aim aim = BowAimbotTrajectory.solve(3.0, 20, 0, 0,
			0, 0, 0, true);

		assertNotNull(aim);
		assertTrue(aim.pitch() > -10 && aim.pitch() < 0,
			"expected the flat solution, was " + aim.pitch());
	}

	@Test
	void fallsBackToTheHighestArrivalWhenOutOfRange()
	{
		// 1.2 格/tick 的初速打 40 格：够不到，应该给出「能飞到最高」的角度
		double speed = 1.2;
		double distance = 40;
		BowAimbotTrajectory.Aim aim =
			BowAimbotTrajectory.solve(speed, distance, 0, 0, 0, 0, 0, true);

		assertNotNull(aim);
		assertTrue(hitError(speed, aim.yaw(), aim.pitch(), 0, 0, 0, true,
			distance, 0, 0) > 1, "should not be a hit");

		double arrival = Math.cos(Math.toRadians(aim.pitch())) * speed;
		double vertical = -Math.sin(Math.toRadians(aim.pitch())) * speed;
		double best = Double.NEGATIVE_INFINITY;
		for(double pitch = -89.9; pitch <= 89.9; pitch += 0.1)
		{
			double y = BowAimbotTrajectory.heightAtDistance(
				Math.cos(Math.toRadians(pitch)) * speed,
				-Math.sin(Math.toRadians(pitch)) * speed, distance);
			if(!Double.isNaN(y) && y > best)
				best = y;
		}

		assertTrue(best > Double.NEGATIVE_INFINITY);
		assertTrue(BowAimbotTrajectory.heightAtDistance(arrival, vertical,
			distance) >= best - 0.001,
			"fallback should arrive as high as possible");
	}

	@Test
	void returnsNullOnlyWhenTheGeometryIsDegenerate()
	{
		// 目标在正上方/正下方：水平距离为 0，交给调用方直接对着目标看
		assertNull(BowAimbotTrajectory.solve(3, 0, 5, 0, 0, 0, 0, true));
		assertNull(BowAimbotTrajectory.solve(3, 0, -5, 0, 0, 0, 0, true));

		// 没有初速
		assertNull(BowAimbotTrajectory.solve(0, 20, 0, 0, 0, 0, 0, true));
	}

	@Test
	void survivesFloatRoundedRotations()
	{
		// 实际下发的角度是 float，取整之后误差只有 1e-7 格量级
		BowAimbotTrajectory.Aim aim = BowAimbotTrajectory.solve(3.0, 20, 0, 0,
			0, 0, 0.2158, true);

		assertNotNull(aim);
		double error = hitError(3.0, (float)aim.yaw(), (float)aim.pitch(), 0, 0,
			0.2158, true, 20, 0, 0);
		assertTrue(error < 1.0E-5, "float rounding error was " + error);
	}

	@Test
	void ignoresTheShootersFallSpeedWhileOnTheGround()
	{
		// 同样的输入，只差 shooterOnGround：在地面上时箭不继承 vy
		BowAimbotTrajectory.Aim onGround =
			BowAimbotTrajectory.solve(3.0, 20, -2, 0, 0, -1.0, 0, true);
		BowAimbotTrajectory.Aim airborne =
			BowAimbotTrajectory.solve(3.0, 20, -2, 0, 0, -1.0, 0, false);

		assertNotNull(onGround);
		assertNotNull(airborne);
		assertTrue(airborne.pitch() < onGround.pitch() - 10,
			"airborne aim must point higher to cancel the inherited fall");

		assertHits(3.0, 20, -2, 0, -1.0, 0, true);
		assertHits(3.0, 20, -2, 0, -1.0, 0, false);
	}

	/**
	 * 解算一次并断言独立模拟的命中误差足够小。
	 */
	private static void assertHits(double speed, double distance, double height,
		double shooterVx, double shooterVy, double shooterVz,
		boolean shooterOnGround)
	{
		double dx = distance;
		BowAimbotTrajectory.Aim aim = BowAimbotTrajectory.solve(speed, dx,
			height, 0, shooterVx, shooterVy, shooterVz, shooterOnGround);

		assertNotNull(aim, "no solution for " + speed + " / " + distance + " / "
			+ height + " / " + shooterVz);

		double error = hitError(speed, aim.yaw(), aim.pitch(), shooterVx,
			shooterVy, shooterVz, shooterOnGround, dx, height, 0);

		assertTrue(error < 1.0E-6, "hit error " + error + " for " + speed + " / "
			+ distance + " / " + height + " / " + shooterVz + " (aim "
			+ aim.yaw() + ", " + aim.pitch() + ")");
	}

	/**
	 * 按 1.20.1 的 Projectile/AbstractArrow 独立模拟，返回箭经过目标水平距离时
	 * 与目标点的距离；飞不到那里时返回 NaN。
	 */
	private static double hitError(double speed, double yaw, double pitch,
		double shooterVx, double shooterVy, double shooterVz,
		boolean shooterOnGround, double tx, double ty, double tz)
	{
		double yawRadians = Math.toRadians(yaw);
		double pitchRadians = Math.toRadians(pitch);

		// Projectile.shootFromRotation：方向 + 射手速度
		double vx = -Math.sin(yawRadians) * Math.cos(pitchRadians) * speed
			+ shooterVx;
		double vy = -Math.sin(pitchRadians) * speed
			+ (shooterOnGround ? 0 : shooterVy);
		double vz = Math.cos(yawRadians) * Math.cos(pitchRadians) * speed
			+ shooterVz;

		double distance = Math.sqrt(tx * tx + tz * tz);
		double x = 0;
		double y = 0;
		double z = 0;

		for(int tick = 0; tick < 400; tick++)
		{
			double previousX = x;
			double previousY = y;
			double previousZ = z;
			double previousRadius = Math.sqrt(x * x + z * z);

			x += vx;
			y += vy;
			z += vz;

			double radius = Math.sqrt(x * x + z * z);
			if(radius >= distance && previousRadius <= distance)
			{
				double t = radius == previousRadius ? 0
					: (distance - previousRadius) / (radius - previousRadius);
				double hitX = previousX + (x - previousX) * t;
				double hitY = previousY + (y - previousY) * t;
				double hitZ = previousZ + (z - previousZ) * t;

				return Math.sqrt((hitX - tx) * (hitX - tx)
					+ (hitY - ty) * (hitY - ty) + (hitZ - tz) * (hitZ - tz));
			}

			// AbstractArrow.tick：先位移，再阻力，再重力
			vx *= 0.99;
			vy = vy * 0.99 - 0.05;
			vz *= 0.99;
		}

		return Double.NaN;
	}

	/**
	 * 原实现用的无阻力抛物线公式：{@code g = 0.006} 配归一化拉弓力度。
	 */
	private static double oldFormulaPitch(double power, double distance,
		double height)
	{
		double g = 0.006;
		double velocitySq = power * power;
		double velocityPow4 = velocitySq * velocitySq;
		double tmp = velocityPow4
			- g * (g * distance * distance + 2 * height * velocitySq);

		return -Math.toDegrees(
			Math.atan((velocitySq - Math.sqrt(tmp)) / (g * distance)));
	}

	/**
	 * 目标方向对应的偏航角，与原实现的 {@code atan2(dz, dx) - 90} 一致。
	 */
	private static double directYaw(double dx, double dz)
	{
		return Math.toDegrees(Math.atan2(dz, dx)) - 90;
	}
}
