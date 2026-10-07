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
 * 按**显示尺寸**给图层贴图降采样。
 *
 * <p>
 * 起因是实测到的浪费：某个场景给一根钟表指针用了 2000×2000 的贴图，屏幕上却只有几根
 * 细针那么细，四层指针与圆点合计 20M 像素（约 80 MB 显存），直接把 64M 像素的预算吃光、
 * 把后面的层挤掉。Wallpaper Engine 官方文档也承认这类浪费（它建议作者导入时把图层裁剪到
 * 最小尺寸，并推荐整体控制在 ~300MB 显存内），但**素材已经发布出去的没法改**，所以只能
 * 在渲染端补。
 * </p>
 *
 * <p>
 * <b>前提条件（不是可选优化，是安全要求）</b>：只有场景自己声明了尺寸时才敢降。
 * 因为 {@code WeSceneLayout.rect} 是这么算图层矩形的：
 * {@code layer.sizeX() > 0 ? layer.sizeX() : textureWidth} —— 一旦场景没声明尺寸，
 * 布局就是**拿贴图尺寸当显示尺寸**的，这时降采样会把图层画小、位置也会错。
 * 所以 {@link #target} 在 {@code sizeX/sizeY <= 0} 时一律返回 null。
 * </p>
 */
final class LayerResampler
{
	private LayerResampler()
	{
	}

	/** 贴图比显示尺寸大出这个倍数才值得降。 */
	static final int MIN_FACTOR = 2;

	/** 再离谱的素材也不缩超过这个倍数。 */
	static final int MAX_FACTOR = 16;

	/**
	 * 安全系数：拿显示尺寸的这么多倍当目标。
	 *
	 * <p>
	 * 留余量是因为图层还可能被放大 —— 窗口比画布大时 {@code scale > 1}、视差位移、
	 * 用户缩放都会让实际显示尺寸超过画布单位下的 {@code size}。留 2 倍是保守取法，
	 * 代价只是少省一点显存。
	 * </p>
	 */
	static final float HEADROOM = 2;

	/**
	 * 该不该降、降到多少。
	 *
	 * @param sizeX
	 *            场景声明的图层宽度（画布单位）；{@code <= 0} 表示没声明
	 * @return {@code {宽, 高}}；不需要降采样时返回 {@code null}
	 */
	static int[] target(int textureWidth, int textureHeight, float sizeX,
		float sizeY)
	{
		// 没声明尺寸就别动：布局此时依赖贴图尺寸
		if(textureWidth <= 0 || textureHeight <= 0 || sizeX <= 0 || sizeY <= 0)
			return null;

		float displayLongest = Math.max(sizeX, sizeY) * HEADROOM;
		float textureLongest = Math.max(textureWidth, textureHeight);

		if(displayLongest <= 0 || textureLongest < displayLongest * MIN_FACTOR)
			return null;

		float factor =
			Math.min(MAX_FACTOR, textureLongest / displayLongest);
		int width = Math.max(1, Math.round(textureWidth / factor));
		int height = Math.max(1, Math.round(textureHeight / factor));

		// 缩完还是原尺寸（或更大）就没意义
		if(width >= textureWidth && height >= textureHeight)
			return null;

		return new int[]{width, height};
	}

	/**
	 * 面积平均降采样。每个目标像素取源图对应矩形区域的加权平均。
	 *
	 * <p>
	 * <b>alpha 按预乘处理</b>：直接对 R/G/B 求平均会让半透明边缘的颜色渗进透明区域，
	 * 抠像素材的边上会出现一圈脏色。所以先按 alpha 加权求平均，再还原回直通 alpha。
	 * </p>
	 *
	 * @return 新图；调用方负责关掉 {@code source}
	 */
	static NativeImage downscale(NativeImage source, int targetWidth,
		int targetHeight)
	{
		int sourceWidth = source.getWidth();
		int sourceHeight = source.getHeight();

		if(targetWidth <= 0 || targetHeight <= 0 || targetWidth > sourceWidth
			|| targetHeight > sourceHeight)
			throw new IllegalArgumentException("降采样目标尺寸非法：" + targetWidth
				+ "x" + targetHeight + "（源 " + sourceWidth + "x"
				+ sourceHeight + "）");

		NativeImage out = new NativeImage(NativeImage.Format.RGBA, targetWidth,
			targetHeight, false);

		for(int y = 0; y < targetHeight; y++)
		{
			int fromY = (int)((long)y * sourceHeight / targetHeight);
			int toY = Math.max(fromY + 1,
				(int)((long)(y + 1) * sourceHeight / targetHeight));

			for(int x = 0; x < targetWidth; x++)
			{
				int fromX = (int)((long)x * sourceWidth / targetWidth);
				int toX = Math.max(fromX + 1,
					(int)((long)(x + 1) * sourceWidth / targetWidth));

				long red = 0;
				long green = 0;
				long blue = 0;
				long alpha = 0;
				int count = 0;

				for(int sy = fromY; sy < toY; sy++)
					for(int sx = fromX; sx < toX; sx++)
					{
						// NativeImage 的整数像素接口是 ABGR：低字节是红
						int pixel = source.getPixelRGBA(sx, sy);
						int a = pixel >>> 24;
						red += (pixel & 0xFF) * a;
						green += (pixel >> 8 & 0xFF) * a;
						blue += (pixel >> 16 & 0xFF) * a;
						alpha += a;
						count++;
					}

				if(count == 0)
					count = 1;

				int outAlpha = (int)(alpha / count);
				int outRed = alpha == 0 ? 0 : (int)(red / alpha);
				int outGreen = alpha == 0 ? 0 : (int)(green / alpha);
				int outBlue = alpha == 0 ? 0 : (int)(blue / alpha);

				out.setPixelRGBA(x, y,
					outAlpha << 24 | outBlue << 16 | outGreen << 8 | outRed);
			}
		}

		return out;
	}
}
