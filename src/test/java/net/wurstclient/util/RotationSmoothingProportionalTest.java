/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.util.Mth;

final class RotationSmoothingProportionalTest
{
	private static float length(Rotation delta)
	{
		return (float)Math.sqrt(delta.yaw() * delta.yaw()
			+ delta.pitch() * delta.pitch());
	}

	@Test
	void existingModesStillUsePerAxisLimits()
	{
		// 回归：加 applyVector 之前，Linear 是逐轴限速、俯仰只拿 0.7 倍
		Rotation linear = RotationSmoothing.smooth(new Rotation(0, 0),
			new Rotation(90, 45), 10, RotationSmoothing.LINEAR);
		assertEquals(10F, linear.yaw(), 1E-4);
		assertEquals(7F, linear.pitch(), 1E-4);

		Rotation instant = RotationSmoothing.smooth(new Rotation(0, 0),
			new Rotation(90, 45), 10, RotationSmoothing.INSTANT);
		assertEquals(90F, instant.yaw(), 1E-4);
		assertEquals(45F, instant.pitch(), 1E-4);
	}

	@Test
	void proportionalSpendsTheWholeStepAcrossBothAxes()
	{
		Rotation step = RotationSmoothing.smooth(new Rotation(0, 0),
			new Rotation(90, 30), 30, RotationSmoothing.PROPORTIONAL);

		// 步长正好是 maxChange
		assertEquals(30F, length(step), 1E-3);

		// 两轴按 90:30 的比例分配（Linear 会是 30:21）
		assertEquals(3F, step.yaw() / step.pitch(), 1E-3);

		Rotation linear = RotationSmoothing.smooth(new Rotation(0, 0),
			new Rotation(90, 30), 30, RotationSmoothing.LINEAR);
		assertEquals(1.4286F, linear.yaw() / linear.pitch(), 1E-3);
	}

	@Test
	void proportionalReachesTargetInOneStepWhenCloseEnough()
	{
		Rotation target = new Rotation(12, -7);
		Rotation step = RotationSmoothing.smooth(new Rotation(0, 0), target,
			30, RotationSmoothing.PROPORTIONAL);

		assertEquals(target.yaw(), step.yaw(), 1E-4);
		assertEquals(target.pitch(), step.pitch(), 1E-4);
	}

	@Test
	void proportionalTakesTheShortWayAroundInYaw()
	{
		Rotation step = RotationSmoothing.smooth(new Rotation(170, 0),
			new Rotation(-170, 0), 30, RotationSmoothing.PROPORTIONAL);

		// 170 -> -170 的短路径是 +20 度，一步就能到
		assertEquals(-170F, Mth.wrapDegrees(step.yaw()), 1E-3);
	}

	@Test
	void proportionalRespectsPitchClamp()
	{
		Rotation step = RotationSmoothing.smooth(new Rotation(0, 0),
			new Rotation(0, 200), 1000, RotationSmoothing.PROPORTIONAL);

		assertEquals(90F, step.pitch(), 1E-4);
	}

	@Test
	void proportionalConvergesWithoutOvershooting()
	{
		Rotation current = new Rotation(0, 0);
		Rotation target = new Rotation(90, 30);
		int ticks = 0;

		while(ticks < 100)
		{
			Rotation next = RotationSmoothing.smooth(current, target, 5,
				RotationSmoothing.PROPORTIONAL);

			float moved = length(new Rotation(
				Mth.wrapDegrees(next.yaw() - current.yaw()),
				next.pitch() - current.pitch()));
			assertTrue(moved <= 5.001F || next.equals(target),
				"单 tick 移动量不应超过 maxChange: " + moved);

			current = next;
			ticks++;
			if(current.yaw() == target.yaw() && current.pitch() == target.pitch())
				break;
		}

		assertTrue(ticks < 100, "PROPORTIONAL 应当收敛");
		assertEquals(target.yaw(), current.yaw(), 1E-3);
		assertEquals(target.pitch(), current.pitch(), 1E-3);
	}
}
