/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.wurstclient.background.WeParticles.Particle;

/**
 * Tests the snow particle preset and simulation.
 *
 * <p>
 * The fixture is the shape of the two presets Persica ships, so the numbers here
 * are the real ones (rate 15/s, lifetime 15-23s, size 2-30, velocity pointing
 * down and slightly left, 0.1s fade in). What the tests guard is that the
 * simulation is predictable: same seed, same particles, nothing spawning before
 * {@code starttime}, nothing outliving its lifetime, and the count staying under
 * {@code maxcount} no matter how long it runs.
 */
final class WeParticlesTest
{
	private static final String SNOW = """
		{
			"emitter": [{
				"directions": "3 3 0", "distancemax": 1200, "distancemin": 10,
				"id": 6, "name": "sphererandom", "rate": 15
			}],
			"initializer": [
				{"id": 2, "max": 23, "min": 15, "name": "lifetimerandom"},
				{"id": 3, "max": 30, "min": 2, "name": "sizerandom"},
				{"id": 4, "max": "-37 -90 0", "min": "-10 -50 0",
				 "name": "velocityrandom"},
				{"id": 5, "max": "255 255 255", "min": "95 98 100",
				 "name": "colorrandom"}
			],
			"material": "materials/presets/snowflat.json",
			"maxcount": 100000,
			"operator": [
				{"id": 7, "name": "movement"},
				{"frequencymax": 1.0, "frequencymin": 0.8, "id": 8,
				 "mask": "1 0.5 0", "name": "oscillateposition",
				 "phasemax": 1, "phasemin": 0, "scalemax": 35, "scalemin": 20},
				{"fadeintime": 0.1, "id": 9, "name": "alphafade"}
			],
			"renderer": [{"id": 1, "name": "sprite"}],
			"starttime": 15
		}
		""";

	private static WeParticlePreset snow() throws IOException
	{
		return WeParticlePreset.parse(SNOW);
	}

	@Test
	void itReadsThePreset() throws IOException
	{
		WeParticlePreset preset = snow();

		assertEquals(15, preset.rate());
		assertEquals(10, preset.distanceMin());
		assertEquals(1200, preset.distanceMax());
		assertEquals(15, preset.lifetimeMin());
		assertEquals(23, preset.lifetimeMax());
		assertEquals(2, preset.sizeMin());
		assertEquals(30, preset.sizeMax());
		assertEquals(-10, preset.velocityMinX());
		assertEquals(-50, preset.velocityMinY());
		assertEquals(-37, preset.velocityMaxX());
		// Y 这一轴 min > max（-50 > -90）：取值时自己换过来，见 between()
		assertEquals(-90, preset.velocityMaxY());
		assertEquals(95, preset.colorMin());
		assertEquals(255, preset.colorMax());
		assertEquals(0.1F, preset.fadeInTime(), 1e-6F);
		assertEquals(0.8F, preset.swayFrequencyMin(), 1e-6F);
		assertEquals(20, preset.swayScaleMin());
		assertEquals(35, preset.swayScaleMax());
		assertEquals(1, preset.swayMaskX());
		assertEquals(0.5F, preset.swayMaskY(), 1e-6F);
		assertEquals(100000, preset.maxCount());
		assertEquals(15, preset.startTime());
	}

	@Test
	void itRefusesUnreadablePresets()
	{
		assertThrows(IOException.class, () -> WeParticlePreset.parse(null));
		assertThrows(IOException.class, () -> WeParticlePreset.parse("nope"));
	}

	/** 缺字段走默认值，速率默认 0（宁可不发，也不要按默认值乱喷）。 */
	@Test
	void missingFieldsFallBack() throws IOException
	{
		WeParticlePreset preset = WeParticlePreset.parse("{}");

		assertEquals(0, preset.effectiveRate());
		assertEquals(1, preset.lifetimeMin());
		assertEquals(1, preset.sizeMin());
		assertEquals(1, preset.colorMax() / 255.0F, 1e-6F);
	}

	@Test
	void startTimeDelaysTheFirstFlake() throws IOException
	{
		WeParticles particles = new WeParticles(snow(), 1);

		// starttime 之前一个都不该有（跑满 14 秒）
		for(int i = 0; i < 140; i++)
			particles.advance(0.1F);

		assertEquals(0, particles.count());

		// 过了 starttime 之后按 15/秒 开始发
		for(int i = 0; i < 40; i++)
			particles.advance(0.1F);

		assertTrue(particles.count() >= 10,
			"4 秒只发了 " + particles.count() + " 个");
	}

	/** 速率是每秒多少个：10/秒 跑 1 秒就该有 10 个左右。 */
	@Test
	void itSpawnsAtTheConfiguredRate() throws IOException
	{
		WeParticlePreset preset = new WeParticlePreset(10, 10, 100, 100, 100, 2,
			4, -10, -50, -37, -90, 95, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			1000, 0);
		WeParticles particles = new WeParticles(preset, 2);

		for(int i = 0; i < 10; i++)
			particles.advance(0.1F);

		assertEquals(10, particles.count());
	}

	@Test
	void particlesExpire() throws IOException
	{
		WeParticlePreset preset = new WeParticlePreset(20, 10, 100, 0.5F, 0.5F,
			2, 4, 0, -50, 0, -90, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			1000, 0);
		WeParticles particles = new WeParticles(preset, 3);

		particles.advance(0.1F);
		assertTrue(particles.count() > 0);

		// 寿命 0.5 秒，跑够 1 秒后第一批必然已经回收
		for(int i = 0; i < 20; i++)
			particles.advance(0.05F);

		// 仍在发新的，但在飞的不会超过「速率 × 寿命」太多
		assertTrue(particles.count() <= 20 * 0.5F + 4,
			"回收没生效，攒了 " + particles.count() + " 个");
	}

	/**
	 * 预设空间的 y 是向上的（{@code camera.up = "0 1 0"}），所以初速度取负值是
	 * 「往下落」。这条用例锁住这个约定：y 随时间<b>减小</b>，也就是屏幕上的位置
	 * 在往下走（画的时候才翻符号，见 {@code WeSceneWallpaper}）。
	 */
	@Test
	void particlesFallDownAndDriftLeft() throws IOException
	{
		WeParticlePreset preset = new WeParticlePreset(100, 10, 100, 100, 100,
			2, 4, -10, -50, -37, -90, 255, 255, 0, 0, 0, 0, 0, 0, 0, 1, 0.5F,
			1000, 0);
		WeParticles particles = new WeParticles(preset, 4);

		particles.advance(0.05F);
		List<Particle> first = particles.particles();
		assertTrue(!first.isEmpty());
		float startY = first.get(0).y();
		float startX = first.get(0).x();

		for(int i = 0; i < 20; i++)
			particles.advance(0.05F);

		Particle moved = particles.particles().get(0);
		assertTrue(moved.y() < startY,
			"y 应当减小（往下落），实际 " + startY + " -> " + moved.y());
		assertTrue(moved.x() < startX,
			"x 应当减小（往左飘），实际 " + startX + " -> " + moved.x());
	}

	@Test
	void fadeInAndSwayStayInRange() throws IOException
	{
		WeParticlePreset preset = new WeParticlePreset(100, 10, 100, 100, 100,
			2, 4, 0, 0, 0, 0, 255, 255, 0.2F, 0.5F, 1.5F, 0, 1, 20, 35, 1, 0.5F, 1000, 0);
		WeParticles particles = new WeParticles(preset, 5);

		particles.advance(0.1F);
		Particle particle = particles.particles().get(0);

		// 淡入到一半：age 0.1 / fadein 0.2
		assertEquals(0.5F, particle.alpha(), 0.01F);

		// 摆动不超过预设的幅度
		for(int step = 0; step < 200; step++)
		{
			particles.advance(0.02F);

			for(Particle moving : particles.particles())
			{
				assertTrue(Math.abs(moving.swayX()) <= 35.0001F,
					"摆动幅度越界：" + moving.swayX());
				assertTrue(moving.alpha() >= 0 && moving.alpha() <= 1);
				assertTrue(moving.brightness() >= 0 && moving.brightness() <= 1);
				assertTrue(moving.size() > 0);
			}
		}
	}

	/** 上限是硬的：速率再大也不能超过 maxcount。 */
	@Test
	void theCountStaysUnderTheLimit() throws IOException
	{
		WeParticlePreset preset = new WeParticlePreset(1000, 10, 100, 1000,
			1000, 2, 4, 0, 0, 0, 0, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0,
			0, 50, 0);
		WeParticles particles = new WeParticles(preset, 6);

		for(int i = 0; i < 200; i++)
			particles.advance(0.1F);

		assertEquals(50, particles.count());
	}

	/** 同一种子必须给出同一场雪，否则没法给"飘到哪了"写断言。 */
	@Test
	void theSameSeedGivesTheSameSnow() throws IOException
	{
		WeParticles first = new WeParticles(snow(), 7);
		WeParticles second = new WeParticles(snow(), 7);

		for(int i = 0; i < 400; i++)
		{
			first.advance(0.05F);
			second.advance(0.05F);
		}

		assertEquals(first.count(), second.count());
		assertTrue(first.count() > 0);

		for(int i = 0; i < first.count(); i++)
		{
			assertEquals(first.particles().get(i).x(),
				second.particles().get(i).x(), 1e-4F);
			assertEquals(first.particles().get(i).y(),
				second.particles().get(i).y(), 1e-4F);
		}
	}

	/** 一帧的步长有上限：卡顿一次不该补出成百上千个粒子。 */
	@Test
	void aLongStallDoesNotSpawnABurst() throws IOException
	{
		WeParticlePreset preset = new WeParticlePreset(100, 10, 100, 100, 100,
			2, 4, 0, 0, 0, 0, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0,
			1000, 0);

		WeParticles stalling = new WeParticles(preset, 8);
		stalling.advance(5F);

		WeParticles steady = new WeParticles(preset, 8);
		steady.advance(0.1F);

		assertEquals(steady.count(), stalling.count());
	}
}
