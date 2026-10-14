/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.background;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * 粒子贴图如果是**帧图集**，这里是它的帧格。
 *
 * <p>
 * 起因：`particle/fog/fog1` 是 **8×8 共 64 帧、每帧 128×128** 的雾动画（每帧一团白烟
 * 落在黑底上，见 {@code docs/particle-sheet-fog1.png}）。我原来把整张 1024×1024 当一帧
 * 采样 —— 一颗雾粒子画出 64 团白烟，屏上就是那片格子状的光晕。WE 的
 * {@code genericparticle.vert} 走 {@code SPRITESHEET} 分支，用
 * {@code ComputeSpriteFrame(frac(lifetime), …)} 按寿命取**一帧**。</p>
 *
 * <p>
 * <b>帧格不硬编码，按自相似量</b>。同一张贴图沿轴平移**一个帧宽**后应当几乎重合，
 * 平移半帧则不重合。实测三个样本：</p>
 *
 * <pre>
 * particle/fog/fog1  平移128 → 2.58/8.31   平移64 → 21.78   ⇒ 是图集，帧128（8x8）
 * particle/fog/fog3  平移128 → 27.99/69.66 平移64 → 41.88   ⇒ 不是（无周期）单帧
 * particle/halo_6    平移 32/64 全 0.00                     ⇒ 不是，单帧
 * </pre>
 *
 * <p>
 * 判据用**投影**：沿另一轴把亮度求和得到一维剖面，再看剖面的相对残差。理由是帧与帧
 * 内容本来就不一样，逐像素比会被帧间差异淹没 —— 第一版就是逐像素 + 绝对阈值 20，
 * 于是把 `fog3` 判成了单帧（它明明是 8×8 图集，逐像素残差却到 27.99）。投影把帧间差异
 * 平均掉之后，两个雾贴图都能稳定量出 8×8。</p>
 */
public record WeParticleSheet(int columns, int rows)
{
	/** 相对残差阈值（残差 ÷ 剖面均值）：大于它就不认为是图集。 */
	private static final double MAX_RESIDUAL = 0.25;

	/** 比值阈值：一个帧宽的残差要显著小于半帧的。 */
	private static final double RATIO = 0.6;

	/** 单帧（不是图集）。 */
	public static final WeParticleSheet SINGLE = new WeParticleSheet(1, 1);

	public boolean isSheet()
	{
		return columns > 1 || rows > 1;
	}

	public int frames()
	{
		return columns * rows;
	}

	/** 某一帧在贴图里的列/行。 */
	public int columnOf(int frame)
	{
		return frame % columns;
	}

	public int rowOf(int frame)
	{
		return frame / columns;
	}

	/**
	 * 寿命进度 → 帧号（与 {@code ComputeSpriteFrame} 同语义：{@code floor(f * 帧数)}）。
	 */
	public int frameOf(float lifeFraction)
	{
		int frames = frames();
		int frame = (int)Math.floor(lifeFraction * frames);
		return Math.max(0, Math.min(frames - 1, frame));
	}

	/**
	 * 从贴图自身量帧格。正方形分格，所以只用 x/y 各自的周期里较小的那个。
	 *
	 * @return 单帧贴图返回 {@link #SINGLE}
	 */
	public static WeParticleSheet detect(NativeImage image)
	{
		if(image == null)
			return SINGLE;

		int width = image.getWidth();
		int height = image.getHeight();

		if(width < 32 || height < 32 || width != height)
			return SINGLE;

		// 帧数从小到大试：找到最小的那个"平移一帧就重合"的周期
		for(int frames = 2; frames <= 16; frames *= 2)
		{
			if(width % frames != 0)
				continue;

			int period = width / frames;
			double residual = residual(image, period);

			if(residual > MAX_RESIDUAL)
				continue;

			// 半帧必须明显更不重合，才说明 period 是"一帧"而不是"随便一个平移"
			double half = residual(image, period / 2);

			if(period / 2 > 0 && residual < RATIO * half)
				return new WeParticleSheet(frames, frames);
		}

		return SINGLE;
	}

	/** 沿 x、y 各平移 period 后的相对残差，取两者的均值。 */
	private static double residual(NativeImage image, int period)
	{
		if(period <= 0)
			return Double.MAX_VALUE;

		return (profileResidual(image, period, true)
			+ profileResidual(image, period, false)) / 2;
	}

	/**
	 * 一维剖面的**相对**残差：沿另一轴把亮度求和得到剖面，平移一个周期的平均差 ÷ 平均值。
	 *
	 * <p>
	 * 用投影而不是逐像素差：帧图集里每一帧的内容都不一样，逐像素比会被帧间差异淹没
	 * （`fog3` 就是这么被第一版判成单帧的：它明明是 8×8 图集，逐像素残差却到 27.99）；
	 * 沿另一轴求和之后，帧间差异被平均掉，剩下的是"每帧在同一相对位置都有东西"这个周期
	 * 结构。</p>
	 *
	 * <p>
	 * 归一是必需的：不同贴图整体亮度差很多，绝对值没法共用阈值。全黑/全白的图直接返回
	 * 最大值 —— 它平移多少都"相等"，别被这一点骗过去。</p>
	 */
	private static double profileResidual(NativeImage image, int period,
		boolean horizontal)
	{
		if(period <= 0)
			return Double.MAX_VALUE;

		int length = horizontal ? image.getWidth() : image.getHeight();
		int other = horizontal ? image.getHeight() : image.getWidth();
		double[] profile = new double[length];
		double mean = 0;

		for(int i = 0; i < length; i++)
		{
			double sum = 0;
			int count = 0;

			for(int j = 0; j < other; j += 2)
			{
				sum += luma(horizontal ? image.getPixelRGBA(i, j)
					: image.getPixelRGBA(j, i));
				count++;
			}

			profile[i] = count == 0 ? 0 : sum / count;
			mean += profile[i];
		}

		mean /= length;

		if(mean <= 1)
			return Double.MAX_VALUE;

		double difference = 0;

		for(int i = 0; i < length; i++)
			difference += Math.abs(profile[i] - profile[(i + period) % length]);

		return difference / length / mean;
	}

	private static int luma(int pixel)
	{
		int red = (pixel >> 16) & 0xFF;
		int green = (pixel >> 8) & 0xFF;
		int blue = pixel & 0xFF;
		return (red * 299 + green * 587 + blue * 114) / 1000;
	}
}
