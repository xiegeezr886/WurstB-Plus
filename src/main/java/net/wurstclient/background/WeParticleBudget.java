/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is governed by the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

/**
 * 一个场景最多收几套粒子。
 *
 * <p>
 * 以前是一个拍脑袋的 {@code MAX_PARTICLE_LAYERS = 8}，实测「流浪地球」有
 * **51 套**、只画了 8 套（84% 的粒子根本不存在）。现在按**量出来的开销**收：
 * 稳态颗数与铺开的面积（见 {@link WeParticlePreset#steadyArea()}）。</p>
 *
 * <p>
 * 实测两个场景的全部系统合起来是多少（这就是定这三个数的依据）：</p>
 *
 * <pre>
 * 佩丽卡     5 套 /  256 颗 /  42.3M 画布像素²（画布 4M → 8.5 屏）
 * 流浪地球  51 套 / 1512 颗 / 100.5M 画布像素²（画布 8M → 12.5 屏）
 * </pre>
 *
 * <p>
 * 所以默认值取「32 套 / 6000 颗 / 24 屏」：两个场景都**收得下全部**（51 套那个
 * 会按 32 套截，见下），而 24 屏是给病态作品的闸 —— 作者要的是十几屏的柔光叠加，
 * WE 自己也是这么画的，不该由我打折；但几百屏就得拦。</p>
 *
 * <p>
 * 只收 32 套而不是全收，是因为 51 套里后半段多是一颗一颗的拖尾/火花，
 * 少几套不影响观感，而**每套都要一趟 advance + 逐颗 blit**。被丢掉时
 * {@code WeSceneWallpaper} 会打一行日志说清丢了几套、被哪条预算拦的。</p>
 */
public record WeParticleBudget(int maxSystems, double maxParticles,
	double maxArea)
{
	/** 24 屏：见类注释里的实测数字。 */
	public static final WeParticleBudget DEFAULT =
		new WeParticleBudget(32, 6000, 24);

	/**
	 * 把「几屏」换算成画布像素²。
	 *
	 * <p>
	 * {@code maxArea} 的单位就是**屏数**（画布铺满一次算一屏），所以直接乘画布面积 ——
	 * 我第一版在这里多除了一次固定的 4M，等于把上限又乘了 {@code 画布/4M}，
	 * 结果佩丽卡（8.5 屏）被自己的预算拦掉了，单测当场抓住。</p>
	 */
	public double areaLimit(int canvasWidth, int canvasHeight)
	{
		return Math.max(0, maxArea) * canvasWidth * (double)canvasHeight;
	}

	/**
	 * 按顺序收系统，返回收下的个数。
	 *
	 * <p>
	 * 顺序就是场景里的顺序（WE 也是这个顺序画的），超预算时**从后面截** ——
	 * 不去挑"最贵的先扔"：那会让同一张壁纸里前后两套同类粒子一套有一套没有，
	 * 比少几套更难看。第一个系统无论如何都收（否则一个都不画，等于整层消失）。</p>
	 *
	 * @param particles
	 *            每套的稳态颗数
	 * @param areas
	 *            每套铺开的画布面积
	 * @param areaLimit
	 *            面积上限（画布像素²）
	 */
	public int admit(double[] particles, double[] areas, double areaLimit)
	{
		int count = Math.min(particles.length, areas.length);
		int admitted = 0;
		double totalParticles = 0;
		double totalArea = 0;

		for(int i = 0; i < count; i++)
		{
			if(admitted >= maxSystems)
				break;

			double nextParticles = totalParticles + Math.max(0, particles[i]);
			double nextArea = totalArea + Math.max(0, areas[i]);

			if(admitted > 0 && (nextParticles > maxParticles
				|| nextArea > areaLimit))
				break;

			totalParticles = nextParticles;
			totalArea = nextArea;
			admitted++;
		}

		return admitted;
	}

	/** 被哪条预算拦住的，用来打日志。 */
	public String limiter(double[] particles, double[] areas, double areaLimit,
		int admitted)
	{
		if(admitted >= maxSystems)
			return "套数上限 " + maxSystems;

		double totalParticles = 0;
		double totalArea = 0;

		for(int i = 0; i <= admitted && i < particles.length; i++)
		{
			totalParticles += Math.max(0, particles[i]);
			totalArea += Math.max(0, areas[i]);
		}

		if(totalParticles > maxParticles)
			return "颗数上限 " + (long)maxParticles;

		if(totalArea > areaLimit)
			return "面积上限 " + Math.round(areaLimit / 1_000_000) + "M";

		return "收完了";
	}
}
