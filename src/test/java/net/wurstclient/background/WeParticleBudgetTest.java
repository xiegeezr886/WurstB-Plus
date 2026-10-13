/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 粒子预算（{@link WeParticleBudget}）。
 *
 * <p>
 * 定这三个数的实测依据写在类注释里：佩丽卡 5 套 / 256 颗 / 42.3M，流浪地球 51 套 /
 * 1512 颗 / 100.5M。这里钉住"两个真实场景都收得下"以及三条闸各自生效。
 * </p>
 */
final class WeParticleBudgetTest
{
	private static final WeParticleBudget BUDGET = WeParticleBudget.DEFAULT;

	/** 实测：佩丽卡 5 套加起来 42.3M 画布面积，画布 3440x1440 → 必须全收。 */
	@Test
	void admitsTheRealPerlicaScene()
	{
		double[] particles = {240, 6, 6, 4, 0};
		double[] areas = {240 * 9.5 * 9.5, 6 * 1600 * 1600, 6 * 1600 * 1600,
			4 * 1700 * 1700, 0};
		double limit = BUDGET.areaLimit(3440, 1440);

		assertEquals(5, BUDGET.admit(particles, areas, limit));
	}

	/**
	 * 实测：流浪地球 51 套 / 1512 颗 / 100.5M，画布 3840x2160。
	 * 面积与颗数都收得下，是**套数上限 32** 在起作用。
	 */
	@Test
	void admitsUpToTheSystemCapForTheRealWanderingEarthScene()
	{
		double[] particles = new double[51];
		double[] areas = new double[51];

		java.util.Arrays.fill(particles, 30);
		java.util.Arrays.fill(areas, 2_000_000);
		double limit = BUDGET.areaLimit(3840, 2160);

		// 51 * 30 = 1530 颗、51 * 2M = 102M，都没到上限；断在套数
		assertTrue(1530 < BUDGET.maxParticles());
		assertTrue(102_000_000 < limit);
		assertEquals(BUDGET.maxSystems(), BUDGET.admit(particles, areas, limit));
	}

	/** 颗数闸：前两套就吃满 6000 颗时，第三套不进。 */
	@Test
	void stopsAtParticleCap()
	{
		double[] particles = {3000, 3000, 100};
		double[] areas = {1, 1, 1};

		assertEquals(2, BUDGET.admit(particles, areas, 1e12));
	}

	/** 面积闸：面积按画布算，画布越大给得越多。 */
	@Test
	void stopsAtAreaCap()
	{
		double[] particles = {10, 10, 10};
		double[] areas = {4e8, 4e8, 10};

		assertEquals(1, BUDGET.admit(particles, areas, 5e8));
		assertEquals(0, BUDGET.areaLimit(0, 0));
		assertTrue(BUDGET.areaLimit(3840, 2160) > BUDGET.areaLimit(1920, 1080));
	}

	/** 第一个系统无论如何都收 —— 否则一个都不画，整层凭空消失。 */
	@Test
	void alwaysAdmitsTheFirstSystem()
	{
		double[] particles = {1e9};
		double[] areas = {1e15};

		assertEquals(1, BUDGET.admit(particles, areas, 1));
	}

	/** 空数组与长度不齐的数组都不该炸。 */
	@Test
	void toleratesOddInput()
	{
		assertEquals(0, BUDGET.admit(new double[0], new double[0], 1e9));
		assertEquals(1, BUDGET.admit(new double[]{1, 2}, new double[]{1}, 1e9));
	}

	/** 拦住时要说清是哪条闸。 */
	@Test
	void namesTheLimiter()
	{
		double[] particles = {10, 10, 10};
		double[] areas = {4e8, 4e8, 10};

		assertEquals("面积上限 500M",
			BUDGET.limiter(particles, areas, 5e8, 1));
		assertEquals("套数上限 " + BUDGET.maxSystems(),
			BUDGET.limiter(new double[]{1}, new double[]{1}, 1e9,
				BUDGET.maxSystems()));
	}
}
