/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 场景里的 sprite 粒子模拟（纯 Java，可单测）。
 *
 * <p>
 * 只做雪花这类效果的三个动作：按速率发射、按初速度积分位置、到期回收；再叠上
 * 预设里的摆动与淡入。位置都用<b>画布单位</b>（和图层同一套坐标），所以画的时候
 * 直接套 {@link WeSceneLayout} 的换算即可。</p>
 *
 * <p>
 * 粒子用固定种子的 {@link Random}，所以同样的时间序列一定得到同样的粒子——否则
 * 就没法给"飘了多少、什么时候消失"写断言。</p>
 */
public final class WeParticles
{
	/** 一帧最多按这么长时间推进，卡顿之后不会一次补出成百上千个粒子。 */
	private static final float MAX_STEP = 0.1F;

	/** 粒子在画布上能飘多远就回收，防止长时间运行后越飘越远。 */
	private static final float FAR_LIMIT = 20000;

	private final WeParticlePreset preset;
	private final Random random;
	private final List<Particle> particles = new ArrayList<>();

	private float clock;
	private float spawnCredit;

	public WeParticles(WeParticlePreset preset, long seed)
	{
		this.preset = preset;
		this.random = new Random(seed);
	}

	public WeParticlePreset preset()
	{
		return preset;
	}

	/** 一个雪花/尘埃；字段是画布单位。 */
	public static final class Particle
	{
		private float x;
		private float y;
		private float velocityX;
		private float velocityY;
		private float size;
		private float lifetime;
		private float age;
		private float brightness;
		private float fadeIn;
		private float swayFrequency;
		private float swayPhase;
		private float swayScale;
		private float swayMaskX;
		private float swayMaskY;

		public float x()
		{
			return x;
		}

		public float y()
		{
			return y;
		}

		public float size()
		{
			return size;
		}

		/** 0..1：预设的 {@code fadeintime} 内淡入，之后恒为 1。 */
		public float alpha()
		{
			return fadeIn <= 0 ? 1 : Math.min(1, age / fadeIn);
		}

		public float brightness()
		{
			return brightness;
		}

		/**
		 * 摆动位移的 x 分量，单位是画布单位。
		 *
		 * <p>
		 * 注意坐标系：Wallpaper Engine 的世界是 <b>y 向上</b>
		 * （{@code camera.up = "0 1 0"}），所以雪的初速度 y 是负的＝往下落；
		 * 而画布坐标是 y 向下。{@link #y()} 给的是预设空间的坐标，画的时候要翻
		 * 一次符号（见 {@code WeSceneWallpaper}），否则雪会往上飘。
		 * </p>
		 */
		public float swayX()
		{
			return swayFrequency <= 0 ? 0 : sway(0.5F) * swayMaskX;
		}

		public float swayY()
		{
			return swayFrequency <= 0 ? 0 : sway(0.5F) * swayMaskY;
		}

		private float sway(float phaseOffset)
		{
			return (float)Math.sin(
				Math.PI * 2 * (swayFrequency * age + swayPhase + phaseOffset))
				* swayScale;
		}
	}

	/**
	 * 推进一帧。
	 *
	 * @param deltaSeconds
	 *            距上一帧的秒数；过大时按 {@link #MAX_STEP} 截断
	 */
	public void advance(float deltaSeconds)
	{
		if(deltaSeconds <= 0)
			return;

		float delta = Math.min(deltaSeconds, MAX_STEP);
		clock += delta;

		spawn(delta);
		integrate(delta);
	}

	private void spawn(float delta)
	{
		// starttime：预设要求延迟一会儿才开始下雪
		if(clock < preset.startTime())
			return;

		spawnCredit += preset.effectiveRate() * delta;

		while(spawnCredit >= 1 && particles.size() < preset.maxCount())
		{
			spawnCredit -= 1;
			particles.add(createParticle());
		}

		if(particles.size() >= preset.maxCount())
			spawnCredit = 0;
	}

	private Particle createParticle()
	{
		Particle particle = new Particle();

		// 发射范围：以图层原点为中心、半径 min..max 的圆环内随机一点
		double angle = random.nextDouble() * Math.PI * 2;
		float radius = between(preset.distanceMin(), preset.distanceMax());
		particle.x = (float)(Math.cos(angle) * radius);
		particle.y = (float)(Math.sin(angle) * radius);

		particle.velocityX =
			between(preset.velocityMinX(), preset.velocityMaxX());
		particle.velocityY =
			between(preset.velocityMinY(), preset.velocityMaxY());
		particle.size = Math.max(0.1F, between(preset.sizeMin(), preset.sizeMax()));
		particle.lifetime =
			Math.max(0.05F, between(preset.lifetimeMin(), preset.lifetimeMax()));
		particle.brightness =
			Math.max(0, Math.min(1, between(preset.colorMin(), preset.colorMax())
				/ 255.0F));
		particle.fadeIn = Math.max(0, preset.fadeInTime());
		particle.swayFrequency = between(preset.swayFrequencyMin(),
			preset.swayFrequencyMax());
		particle.swayPhase =
			between(preset.swayPhaseMin(), preset.swayPhaseMax());
		particle.swayScale =
			between(preset.swayScaleMin(), preset.swayScaleMax());
		particle.swayMaskX = preset.swayMaskX();
		particle.swayMaskY = preset.swayMaskY();

		return particle;
	}

	private void integrate(float delta)
	{
		for(int i = particles.size() - 1; i >= 0; i--)
		{
			Particle particle = particles.get(i);
			particle.age += delta;

			if(particle.age >= particle.lifetime || Math.abs(particle.x) > FAR_LIMIT
				|| Math.abs(particle.y) > FAR_LIMIT)
			{
				particles.remove(i);
				continue;
			}

			particle.x += particle.velocityX * delta;
			particle.y += particle.velocityY * delta;
		}
	}

	private float between(float min, float max)
	{
		if(max < min)
		{
			float swap = min;
			min = max;
			max = swap;
		}

		return min == max ? min : min + random.nextFloat() * (max - min);
	}

	/** 只读视图，画的时候用。 */
	public List<Particle> particles()
	{
		return List.copyOf(particles);
	}

	public int count()
	{
		return particles.size();
	}

	/** 已经跑了多久（秒），摆动相位要用。 */
	public float clock()
	{
		return clock;
	}
}
