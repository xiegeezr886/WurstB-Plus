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

final class RotationGcdPolicyTest
{
	@Test
	void gridStepMatchesVanillaSensitivityFormula()
	{
		// 原版: multiplier = (s * 0.6 + 0.2)^3 * 8，每像素 0.15 度
		double low = Math.pow(0.2, 3) * 8 * 0.15;
		assertEquals(low, RotationGcdPolicy.gridStep(0), 1E-9);

		double mid = Math.pow(0.5 * 0.6 + 0.2, 3) * 8 * 0.15;
		assertEquals(mid, RotationGcdPolicy.gridStep(0.5), 1E-9);

		double high = Math.pow(1.0 * 0.6 + 0.2, 3) * 8 * 0.15;
		assertEquals(high, RotationGcdPolicy.gridStep(1), 1E-9);
	}

	@Test
	void sensitivityIsClampedToVanillaRange()
	{
		assertEquals(RotationGcdPolicy.gridStep(0),
			RotationGcdPolicy.gridStep(-5), 1E-9);
		assertEquals(RotationGcdPolicy.gridStep(1),
			RotationGcdPolicy.gridStep(42), 1E-9);
		assertEquals(RotationGcdPolicy.gridStep(0),
			RotationGcdPolicy.gridStep(Double.NaN), 1E-9);
	}

	@Test
	void patchSnapsToWholePixelsFromPrevious()
	{
		double step = RotationGcdPolicy.gridStep(0.5);

		// 目标偏离锚点 3.4 个像素 -> 3 个像素
		assertEquals(0F, RotationGcdPolicy.patch(0F, 0F, step), 1E-4);
		float threePixels = (float)(3 * step);
		float off = (float)(3.4 * step);
		assertEquals(threePixels, RotationGcdPolicy.patch(off, 0F, step),
			1E-4);

		// 锚点不是 0 时同样成立
		float previous = 123.75F;
		assertEquals(previous + threePixels,
			RotationGcdPolicy.patch(previous + off, previous, step), 1E-4);
	}

	@Test
	void patchKeepsValuesAlreadyOnTheGrid()
	{
		double step = RotationGcdPolicy.gridStep(0.75);
		float previous = -45.5F;
		float onGrid = (float)(previous + 7 * step);

		assertEquals(onGrid,
			RotationGcdPolicy.patch(onGrid, previous, step), 1E-3);
	}

	@Test
	void patchIsIdentityWithoutAGrid()
	{
		// 灵敏度为 0 时步长为 0（无法吸附），应原样返回而不是除零
		assertEquals(37.25F, RotationGcdPolicy.patch(37.25F, 10F, 0), 1E-6);
		assertEquals(37.25F,
			RotationGcdPolicy.patch(37.25F, 10F, Double.NaN), 1E-6);
	}

	@Test
	void patchHandlesNonFiniteInput()
	{
		assertEquals(10F, RotationGcdPolicy.patch(Float.NaN, 10F, 0.5), 1E-6);
		assertEquals(10F,
			RotationGcdPolicy.patch(Float.POSITIVE_INFINITY, 10F, 0.5), 1E-6);
	}

	@Test
	void patchRotationHandlesBothAxesAndNulls()
	{
		double step = RotationGcdPolicy.gridStep(0.5);
		Rotation previous = new Rotation(90F, 20F);
		Rotation target = new Rotation((float)(90 + 2.6 * step),
			(float)(20 - 1.2 * step));

		Rotation patched = RotationGcdPolicy.patch(target, previous, step);
		assertEquals(90F + 3 * step, patched.yaw(), 1E-4);
		assertEquals(20F - 1 * step, patched.pitch(), 1E-4);

		assertEquals(previous, RotationGcdPolicy.patch(null, previous, 0.5));
		Rotation only = new Rotation(1F, 2F);
		assertEquals(only, RotationGcdPolicy.patch(only, null, 0.5));
		// 便捷重载：直接传鼠标灵敏度（0.5）
		assertEquals(patched,
			RotationGcdPolicy.patch(target, previous, 0.5F));
	}

	@Test
	void chainedPatchingKeepsEveryStepOnThePixelGrid()
	{
		double step = RotationGcdPolicy.gridStep(0.5);
		float target = 47.3F;
		float current = 12F;

		for(int i = 0; i < 40; i++)
		{
			float next = RotationGcdPolicy.patch(target, current, step);
			double pixels = (next - current) / step;
			assertEquals(Math.round(pixels), pixels, 1E-3,
				"每一次发送与上一次之间必须是整数个鼠标像素");
			current = next;
		}

		// 收敛到目标半个像素以内（栅格决定不可能正好落在目标上）
		assertEquals(target, current, step);
	}

	@Test
	void resultIsAlwaysWithinHalfAPixel()
	{
		double step = RotationGcdPolicy.gridStep(0.5);
		for(int i = -200; i <= 200; i++)
		{
			float target = i * 1.37F;
			float patched = RotationGcdPolicy.patch(target, 0F, step);
			assertTrue(Math.abs(patched - target) <= step / 2 + 1E-4,
				"吸附误差应不超过半个像素: " + target + " -> " + patched);
		}
	}
}
