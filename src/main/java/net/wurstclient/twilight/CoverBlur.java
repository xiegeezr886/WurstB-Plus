/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

/**
 * 对 ARGB 像素做盒式模糊。
 *
 * <p>
 * 换掉 Skia 之后就没有模糊原语了，而参考项目有两处要用模糊：主页 hero 背后的
 * 封面底图（{@code .hero-ambient}）和歌词页铺满背景的封面。做法是**在小图上
 * 糊一次、把结果当普通纹理用**——按封面切换时算一次，每帧零成本。
 *
 * <p>
 * 两个要点：颜色按**预乘 alpha**累加再还原，否则半透明边缘会把背后的黑色混进
 * 来、糊出来发暗；滑动窗口**读原图写另一份**，原地改会让窗口里混进中间结果，
 * 越糊越花。
 *
 * <p>
 * 故意不含 Minecraft 类型，可以直接用像素数组单测。
 */
public final class CoverBlur
{
	private CoverBlur()
	{
	}

	/**
	 * 原地模糊。{@code radius <= 0}、尺寸非正或数组太小时直接返回。
	 *
	 * @param pixels
	 *            {@code width * height} 个 ARGB 像素，行优先
	 */
	public static void boxBlur(int[] pixels, int width, int height, int radius)
	{
		if(pixels == null || radius <= 0 || width <= 0 || height <= 0
			|| pixels.length < width * height)
			return;

		int clamped = Math.min(radius, Math.min(width, height) / 2);

		if(clamped <= 0)
			return;

		// 读原图、写另一份：横竖两趟都各需要一次拷贝
		int[] source = pixels.clone();
		boxBlurHorizontal(source, pixels, width, height, clamped);
		System.arraycopy(pixels, 0, source, 0, width * height);
		boxBlurVertical(source, pixels, width, height, clamped);
	}

	private static void boxBlurHorizontal(int[] source, int[] target, int width,
		int height, int radius)
	{
		int window = radius * 2 + 1;
		long[] sums = new long[4];

		for(int y = 0; y < height; y++)
		{
			int row = y * width;
			clear(sums);

			for(int i = -radius; i <= radius; i++)
				accumulate(source[row + clamp(i, width)], sums);

			for(int x = 0; x < width; x++)
			{
				target[row + x] = pack(sums, window);
				accumulate(source[row + clamp(x + radius + 1, width)], sums);
				subtract(source[row + clamp(x - radius, width)], sums);
			}
		}
	}

	private static void boxBlurVertical(int[] source, int[] target, int width,
		int height, int radius)
	{
		int window = radius * 2 + 1;
		long[] sums = new long[4];

		for(int x = 0; x < width; x++)
		{
			clear(sums);

			for(int i = -radius; i <= radius; i++)
				accumulate(source[clamp(i, height) * width + x], sums);

			for(int y = 0; y < height; y++)
			{
				target[y * width + x] = pack(sums, window);
				accumulate(source[clamp(y + radius + 1, height) * width + x],
					sums);
				subtract(source[clamp(y - radius, height) * width + x], sums);
			}
		}
	}

	private static void clear(long[] sums)
	{
		sums[0] = 0;
		sums[1] = 0;
		sums[2] = 0;
		sums[3] = 0;
	}

	private static void accumulate(int argb, long[] sums)
	{
		int alpha = argb >>> 24;
		sums[0] += alpha;
		// 预乘后累加，还原时才不会把透明区域的颜色算进来
		sums[1] += (argb >> 16 & 0xFF) * (long)alpha;
		sums[2] += (argb >> 8 & 0xFF) * (long)alpha;
		sums[3] += (argb & 0xFF) * (long)alpha;
	}

	private static void subtract(int argb, long[] sums)
	{
		int alpha = argb >>> 24;
		sums[0] -= alpha;
		sums[1] -= (argb >> 16 & 0xFF) * (long)alpha;
		sums[2] -= (argb >> 8 & 0xFF) * (long)alpha;
		sums[3] -= (argb & 0xFF) * (long)alpha;
	}

	private static int pack(long[] sums, int window)
	{
		long alpha = sums[0] / window;

		if(alpha <= 0 || sums[0] <= 0)
			return 0;

		long red = sums[1] / sums[0];
		long green = sums[2] / sums[0];
		long blue = sums[3] / sums[0];
		return (int)alpha << 24 | (int)red << 16 | (int)green << 8 | (int)blue;
	}

	private static int clamp(int value, int limit)
	{
		return value < 0 ? 0 : value >= limit ? limit - 1 : value;
	}
}
