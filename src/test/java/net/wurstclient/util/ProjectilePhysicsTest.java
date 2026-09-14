/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import net.wurstclient.util.ProjectilePhysics.Velocity;

/**
 * 验证 {@link ProjectilePhysics} 与原版 {@code AbstractArrow}/{@code ThrowableProjectile}
 * 的逐步迭代一致，并留档旧实现（每 tick 十次 {@code scale(0.999)}）的偏差。
 */
public class ProjectilePhysicsTest
{
	private static final double EPSILON = 1E-9;

	/**
	 * 满蓄力弓的水平飞行距离：原版是"用上一 tick 的速度推进位置，然后 v *= 0.99"，
	 * 所以 N tick 后的位移就是 v0 * (1 + 0.99 + ... + 0.99^(N-1)) = v0 * (1-0.99^N)/0.01。
	 */
	@Test
	public void vanillaHorizontalRangeMatchesClosedForm()
	{
		double distance = integrateHorizontally(3.0, 20);

		assertEquals(3.0 * (1 - Math.pow(0.99, 20)) / 0.01, distance, EPSILON);
		// v0 = 3.0（满蓄力弓）、20 tick（1 秒）后原版应该前进约 54.63 格
		assertEquals(54.628, distance, 1E-3);
	}

	/**
	 * 旧实现每 tick 走十次 `p += v * 0.1; v *= 0.999`，等价于把阻力拆成了
	 * `0.999^10 = 0.9900448...`，与真正的 0.99 差 4.5e-5，而且位置的推进用的是
	 * 每个子步里已经衰减过的速度，所以整段轨迹会系统性偏短。
	 */
	@Test
	public void oldSubStepSchemeFallsShort()
	{
		double oldDistance = 0;
		double v = 3.0;
		for(int i = 0; i < 200; i++)
		{
			oldDistance += v * 0.1;
			v *= 0.999;
		}

		double newDistance = integrateHorizontally(3.0, 20);
		assertEquals(54.405, oldDistance, 1E-3);
		assertEquals(0.223, newDistance - oldDistance, 1E-3);
	}

	/** 0.999 并不是 0.99 的十分之一次方：旧实现在每个 tick 上多保留了约 0.0045% 的速度。 */
	@Test
	public void perSubStepDragWasNotExact()
	{
		assertEquals(0.9900449, Math.pow(0.999, 10), 1E-7);
		assertEquals(0.99, Math.pow(Math.pow(0.99, 0.1), 10), EPSILON);
		assertTrue(Math.pow(0.999, 10) > ProjectilePhysics.DRAG);
	}

	/** 同一个 tick 内先乘阻力再减重力，不能反成 (v - g) * drag。 */
	@Test
	public void dragIsAppliedBeforeGravity()
	{
		Velocity after = ProjectilePhysics.dragAndGravity(
			new Velocity(3.0, 3.0, 0), ProjectilePhysics.DRAG, 0.05);

		assertEquals(3.0 * 0.99 - 0.05, after.y(), EPSILON);
		assertEquals(2.92, after.y(), 1E-9);
		assertEquals(3.0 * 0.99, after.x(), EPSILON);
	}

	/** 鱼漂：阻力 0.92、重力 0.03，而且重力在位移推进之前结算。 */
	@Test
	public void fishingBobberUsesItsOwnDragAndOrder()
	{
		Velocity v = new Velocity(0.3, 0.3, 0);
		Velocity beforeMove = ProjectilePhysics.gravity(v, 0.03);
		assertEquals(0.27, beforeMove.y(), EPSILON);

		Velocity afterTick = ProjectilePhysics.drag(beforeMove,
			ProjectilePhysics.FISHING_DRAG);
		assertEquals(0.3 * 0.92, afterTick.x(), EPSILON);
		assertEquals(0.27 * 0.92, afterTick.y(), EPSILON);
	}

	private static double integrateHorizontally(double v0, int ticks)
	{
		double distance = 0;
		Velocity v = new Velocity(v0, 0, 0);
		for(int i = 0; i < ticks; i++)
		{
			distance += v.x();
			v = ProjectilePhysics.dragAndGravity(v, ProjectilePhysics.DRAG, 0.05);
		}
		return distance;
	}
}
