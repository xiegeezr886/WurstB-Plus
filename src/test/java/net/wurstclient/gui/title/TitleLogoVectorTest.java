/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.gui.title;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import net.wurstclient.gui.title.TitleLogoVector.Outline;

/**
 * Tests the vector title logo: the path parser, the rasteriser, and the two
 * numbers that connect them to the screen (the viewBox and the drawn pixel
 * width).
 *
 * <p>
 * The rasteriser is plain Java2D on a {@link java.awt.image.BufferedImage}, so
 * these tests run without a graphics environment - which is the point of doing
 * it this way: the antialiasing is observable in the alpha values instead of
 * having to be eyeballed in a screenshot.
 */
final class TitleLogoVectorTest
{
	private static final String SQUARE = """
		<?xml version="1.0" encoding="UTF-8"?>
		<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
		  <path fill="#FFFFFF" fill-rule="evenodd"
		    d="M 0 0 L 10 0 L 10 10 L 0 10 Z"/>
		</svg>
		""";

	/** A square with a square hole: even-odd must leave the middle empty. */
	private static final String SQUARE_WITH_HOLE = """
		<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
		  <path fill-rule="evenodd"
		    d="M 0 0 L 10 0 L 10 10 L 0 10 Z M 4 4 L 6 4 L 6 6 L 4 6 Z"/>
		</svg>
		""";

	@Test
	void itParsesTheSubset() throws IOException
	{
		Outline outline = TitleLogoVector.parse(SQUARE);

		assertEquals(10, outline.width());
		assertEquals(10, outline.height());
		assertTrue(outline.path().getCurrentPoint() != null);
	}

	@Test
	void itRefusesWhatItCannotDraw()
	{
		assertThrows(IOException.class, () -> TitleLogoVector.parse(null));
		assertThrows(IOException.class, () -> TitleLogoVector.parse(""));
		assertThrows(IOException.class,
			() -> TitleLogoVector.parse("<svg viewBox=\"0 0 10 10\"/>"));
		assertThrows(IOException.class, () -> TitleLogoVector.parse(
			"<svg viewBox=\"0 0 10\"><path d=\"M 0 0 Q 1 1 2 2\"/></svg>"));
		assertThrows(IOException.class, () -> TitleLogoVector.parse(
			"<svg viewBox=\"0 0 10 10 10\"><path d=\"M 0 0\"/></svg>"));
	}

	/** 一个铺满的方块光栅化后应当整块不透明。 */
	@Test
	void aFullSquareIsOpaque() throws IOException
	{
		Outline outline = TitleLogoVector.parse(SQUARE);
		int[] alpha = TitleLogoVector.rasterize(outline, 40, 40);

		assertEquals(1600, alpha.length);

		for(int value : alpha)
			assertEquals(255, value);
	}

	/** evenodd：孔必须真的是空的，且非整数倍缩放时边缘有抗锯齿过渡。 */
	@Test
	void theHoleStaysHollowAndEdgesAreAntialiased() throws IOException
	{
		Outline outline = TitleLogoVector.parse(SQUARE_WITH_HOLE);

		// 10 -> 100 是整数倍，所有边都落在像素网格上，本来就不该有过渡
		int[] aligned = TitleLogoVector.rasterize(outline, 100, 100);
		assertEquals(0, aligned[50 * 100 + 50], "中间的孔被填上了");

		// 10 -> 97 不是整数倍，边缘必须出现过渡像素
		int[] scaled = TitleLogoVector.rasterize(outline, 97, 97);
		int partial = 0;
		for(int value : scaled)
			if(value > 10 && value < 245)
				partial++;

		assertTrue(partial > 0, "边缘一个过渡像素都没有，光栅化没开抗锯齿");
	}

	/** 尺寸怎么变，形状占比都该差不多——这就是矢量的意义。 */
	@Test
	void theShapeScalesWithTheTargetSize() throws IOException
	{
		Outline outline = TitleLogoVector.parse(SQUARE_WITH_HOLE);

		double small = coverage(TitleLogoVector.rasterize(outline, 25, 25));
		double large = coverage(TitleLogoVector.rasterize(outline, 400, 400));

		// 方块 10x10 去掉 2x2 的孔 = 96% 覆盖
		assertEquals(0.96, small, 0.05);
		assertEquals(0.96, large, 0.01);
	}

	/** 同一个矢量在两种尺寸下渲染，边缘的过渡占比也应当接近（缩放无关）。 */
	@Test
	void antialiasingIsSizeIndependent() throws IOException
	{
		Outline outline = TitleLogoVector.parse(SQUARE_WITH_HOLE);

		double small = partialRatio(TitleLogoVector.rasterize(outline, 33, 33));
		double large = partialRatio(TitleLogoVector.rasterize(outline, 233, 233));

		assertTrue(small > 0 && large > 0);
		assertTrue(Math.abs(small - large) < 0.05,
			"过渡占比随尺寸漂移太多：" + small + " vs " + large);
	}

	private static double partialRatio(int[] alpha)
	{
		int partial = 0;

		for(int value : alpha)
			if(value > 10 && value < 245)
				partial++;

		return partial / (double)alpha.length;
	}

	/** 真素材：解析出来必须有轮廓、在 viewBox 内，并且光栅化后形状合理。 */
	@Test
	void theRealVectorLogoRasterises() throws Exception
	{
		var url = TitleLogoVector.class.getResource("/assets/wurst/logo/wurstb.svg");
		assertTrue(url != null, "矢量字标不在 classpath 上");

		String svg;
		try(var stream = url.openStream())
		{
			svg = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}

		Outline outline = TitleLogoVector.parse(svg);

		assertEquals(2101, outline.width());
		assertEquals(660, outline.height());

		var bounds = outline.path().getBounds2D();
		assertTrue(bounds.getMinX() > -1 && bounds.getMinY() > -1,
			"轮廓跑到了 viewBox 外面：" + bounds);
		assertTrue(bounds.getMaxX() < 2102 && bounds.getMaxY() < 661,
			"轮廓跑到了 viewBox 外面：" + bounds);

		int width = 507;
		int height = Math.round(660 * width / 2101F);
		int[] alpha = TitleLogoVector.rasterize(outline, width, height);

		assertEquals(width * height, alpha.length);

		int opaque = 0;
		int partial = 0;

		for(int value : alpha)
		{
			if(value > 128)
				opaque++;

			if(value > 10 && value < 245)
				partial++;
		}

		double coverage = coverage(alpha);
		assertTrue(coverage > 0.10 && coverage < 0.30,
			"字标覆盖率 " + coverage + "，不像一个手写体字标");
		assertTrue(partial > 2000,
			"过渡像素只有 " + partial + " 个——过渡被挤到两端了，看着会硬");
		assertTrue(opaque > 5000, "实心像素只有 " + opaque + " 个");
	}

	/**
	 * 缩小时的核必须把过渡铺开，而且这一点要与尺寸无关。
	 *
	 * <p>
	 * 注意别用"过渡/实心"的比例当判据：那个比例本身就是几何量（周长比面积），
	 * 尺寸一大就自然变小，跟软硬没关系。这里直接量<b>斜坡宽度</b>：某一行里
	 * 连续两个以上过渡像素才算一条斜坡。
	 * </p>
	 */
	@Test
	void edgesStaySoftAtEverySize() throws Exception
	{
		var url = TitleLogoVector.class
			.getResource("/assets/wurst/logo/wurstb.svg");
		String svg;
		try(var stream = url.openStream())
		{
			svg = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}

		Outline outline = TitleLogoVector.parse(svg);

		for(int width : new int[]{338, 507, 900})
		{
			int height = Math.round(660 * width / 2101F);
			int[] alpha = TitleLogoVector.rasterize(outline, width, height);

			int rowsWithRamp = 0;

			for(int y = 0; y < height; y++)
			{
				int run = 0;
				boolean ramp = false;

				for(int x = 0; x < width; x++)
				{
					int value = alpha[y * width + x];

					if(value > 10 && value < 245)
					{
						if(++run >= 2)
							ramp = true;
					}else
						run = 0;
				}

				if(ramp)
					rowsWithRamp++;
			}

			assertTrue(rowsWithRamp > height / 20,
				width + " 像素宽时只有 " + rowsWithRamp + "/" + height
					+ " 行有斜坡，过渡被挤到两端了");
		}
	}

	/** 绘制像素数 = GUI 逻辑宽 × guiScale。 */
	@Test
	void physicalWidthUsesTheGuiScale()
	{
		assertEquals(507, TitleLogoVector.physicalWidth(169, 3));
		assertEquals(338, TitleLogoVector.physicalWidth(169, 2));
		assertEquals(169, TitleLogoVector.physicalWidth(169, 1));
		assertEquals(1, TitleLogoVector.physicalWidth(1, 0.4));
		assertEquals(0, TitleLogoVector.physicalWidth(0, 3));
		assertEquals(0, TitleLogoVector.physicalWidth(169, 0));
	}

	private static double coverage(int[] alpha)
	{
		long sum = 0;

		for(int value : alpha)
			sum += value;

		return sum / (double)(alpha.length * 255);
	}
}
