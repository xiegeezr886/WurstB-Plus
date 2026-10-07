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
	 * 显示尺寸本身是**画布单位**（{@code size × scale}），而图层最终画多大还要乘上
	 * 画布到屏幕的缩放 —— 窗口比画布大、场景带 zoom、视差位移都会把实际显示尺寸顶上去。
	 * 留 1.25 倍余量：屏幕尺寸等于画布时仍不小于一个像素对一个像素，只有用户额外放大
	 * 一倍以上才会开始轻微发虚。
	 * </p>
	 */
	static final float HEADROOM = 1.25F;

	/**
	 * 质量地板：缩到这个像素数以下就不值得了，宁可丢层。
	 *
	 * <p>
	 * 256×256 = 65536。依据：走到这一步的多是叠在上面的 UI 面板与文字框，缩到 256
	 * 见方时文字已经明显发糊 —— 再小就不如不画。而 65536 像素相对 64M 的预算只占
	 * 千分之一，硬塞进来也换不回多少东西。
	 * </p>
	 */
	static final long MIN_PIXELS = 65_536;

	/**
	 * 剩余预算放不下时，算出"刚好放得下"的尺寸。
	 *
	 * <p>
	 * 与 {@link #target} 的区别：{@code target} 是按**显示尺寸**该缩到多少（质量优先），
	 * 这个是按**剩余预算**必须缩到多少（塞得下优先）。两者都按等比缩，保持长宽比。
	 * </p>
	 *
	 * @param room
	 *            剩余可用像素数
	 * @return {@code {宽, 高}}；已经放得下、或缩到质量地板以下都不划算时返回 {@code null}
	 */
	static int[] fitInto(int width, int height, long room)
	{
		if(width <= 0 || height <= 0)
			return null;

		long pixels = (long)width * height;

		if(pixels <= room)
			return null;

		if(room < MIN_PIXELS)
			return null;

		// 面积按比例缩：倍率 = sqrt(现有像素 / 可容纳像素)。
		// 取 floor 保证乘完仍然 <= room。
		double factor = Math.sqrt(pixels / (double)room);
		int targetWidth = Math.max(1, (int)Math.floor(width / factor));
		int targetHeight = Math.max(1, (int)Math.floor(height / factor));

		if((long)targetWidth * targetHeight < MIN_PIXELS)
			return null;

		if(targetWidth >= width && targetHeight >= height)
			return null;

		return new int[]{targetWidth, targetHeight};
	}

	/**
	 * 该不该降、降到多少。
	 *
	 * @param sizeX
	 *            场景声明的图层宽度（画布单位）；{@code <= 0} 表示没声明
	 * @param scaleX
	 *            图层的缩放；{@code <= 0} 按 1 处理。**显示尺寸是 size × scale**
	 *            —— 实测某场景的钟表指针 size=2000、scale=0.25，屏幕上只有 500 见方，
	 *            漏掉 scale 就会把这种 17 倍（圆点更是 105 倍）的浪费判成"没超"。
	 * @return {@code {宽, 高}}；不需要降采样时返回 {@code null}
	 */
	static int[] target(int textureWidth, int textureHeight, float sizeX,
		float sizeY, float scaleX, float scaleY)
	{
		if(textureWidth <= 0 || textureHeight <= 0)
			return null;

		// 安全阀：场景没声明尺寸时，布局是拿贴图尺寸当显示尺寸的
		// （WeSceneLayout.rect：sizeX > 0 ? sizeX : textureWidth），
		// 这时缩小贴图会把图层一并画小、位置也错，所以一律不动。
		if(sizeX <= 0 || sizeY <= 0)
			return null;

		float factorX = scaleX > 0 ? scaleX : 1;
		float factorY = scaleY > 0 ? scaleY : 1;
		float displayLongest =
			Math.max(sizeX * factorX, sizeY * factorY) * HEADROOM;
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
