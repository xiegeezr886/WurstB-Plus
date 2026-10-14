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
 * 判据取「{@code 残差(p) < 20} **且** {@code 残差(p) < 0.5 × 残差(p/2)}」：绝对阈值挡掉
 * "本来就没有周期"的贴图（它们的残差普遍很大），比值阈值挡掉"平整到平移多少都差不多"
 * 的贴图（那种残差处处为 0，比值判据不成立）。这两条一起才把三个样本都判对。</p>
 */
public record WeParticleSheet(int columns, int rows)
{
	/** 绝对阈值：残差大于它就不认为是图集。 */
	private static final double MAX_RESIDUAL = 20;

	/** 比值阈值：一个帧宽的残差要显著小于半帧的。 */
	private static final double RATIO = 0.5;

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

	/** 沿 x、y 各平移 period 后的平均差，取两者的均值（每 4 像素采一个）。 */
	private static double residual(NativeImage image, int period)
	{
		if(period <= 0)
			return Double.MAX_VALUE;

		return (axis(image, period, true) + axis(image, period, false)) / 2;
	}

	private static double axis(NativeImage image, int period, boolean horizontal)
	{
		int width = image.getWidth();
		int height = image.getHeight();
		double sum = 0;
		long count = 0;

		for(int y = 0; y < height; y += 4)
			for(int x = 0; x < width; x += 4)
			{
				int otherX = horizontal ? (x + period) % width : x;
				int otherY = horizontal ? y : (y + period) % height;
				sum += Math.abs(luma(image.getPixelRGBA(x, y))
					- luma(image.getPixelRGBA(otherX, otherY)));
				count++;
			}

		return count == 0 ? 0 : sum / count;
	}

	private static int luma(int pixel)
	{
		int red = (pixel >> 16) & 0xFF;
		int green = (pixel >> 8) & 0xFF;
		int blue = pixel & 0xFF;
		return (red * 299 + green * 587 + blue * 114) / 1000;
	}
}
