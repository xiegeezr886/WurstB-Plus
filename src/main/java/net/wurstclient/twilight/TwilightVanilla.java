/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Style;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.clickgui2.RiseFont;

/**
 * {@link TwilightSkia} 的原版图元后端：用 {@code GuiGraphics} 的矩形、顶点色
 * 渐变和字体渲染画出同一套东西，不再建立 Skia 表面。
 *
 * <p>
 * 为什么要有它：Skia 区域路径每一帧都要把整块区域做一遍 CPU 光栅化，再把
 * {@code 宽 × 高 × 4} 字节上传给 GPU——代价随区域面积线性增长，窗口一满就卡；
 * 那次 NVIDIA 驱动崩溃也发生在这次上传里。圆角、描边、渐变、阴影这几样原版
 * 都能画（圆角与渐变由 {@code RoundedRectRenderer} 的顶点几何给出），代价是
 * 每帧零上传。
 *
 * <p>
 * 唯一真正画不出的能力是模糊：原版没有模糊原语，需要模糊的地方（歌词页封面
 * 背景）改成预先糊好的纹理。裁剪也只能是矩形，圆角裁剪退化为矩形裁剪。
 */
final class TwilightVanilla
{
	/** {@link TwilightGeometry#triangleSpans} 的输出容量，与 Skia 后端同值。 */
	private static final int SPANS = 512;

	private TwilightVanilla()
	{
	}

	// ------------------------------------------------------------------
	// 基础图元
	// ------------------------------------------------------------------

	static void fillRect(GuiGraphics graphics, float x, float y, float width,
		float height, int color)
	{
		if(width <= 0 || height <= 0 || color >>> 24 == 0)
			return;

		graphics.fill(Math.round(x), Math.round(y), Math.round(x + width),
			Math.round(y + height), color);
	}

	static void fillRoundRect(GuiGraphics graphics, float x, float y,
		float width, float height, float radius, int color)
	{
		if(width <= 0 || height <= 0 || color >>> 24 == 0)
			return;

		FlatRenderer.fillRoundedRect(graphics, Math.round(x), Math.round(y),
			Math.round(x + width), Math.round(y + height),
			Math.round(radius), color);
	}

	/**
	 * 每角独立圆角。传入的是 Skia 的 {@code RRect} 数组格式——四个角各占一对
	 * {@code (x, y)}，即 8 个元素，角序为 左上、右上、右下、左下。所以半径在
	 * 下标 0、2、4、6 上，而 {@link FlatRenderer} 那侧要的是紧凑的 4 个值。
	 */
	static void fillRoundRectCorners(GuiGraphics graphics, float x, float y,
		float width, float height, float[] radii, int color)
	{
		if(width <= 0 || height <= 0 || color >>> 24 == 0)
			return;

		FlatRenderer.fillRoundedRectCorners(graphics, x, y, x + width,
			y + height, toFourCorners(radii), color);
	}

	/** Skia 的 8 元素圆角数组 → 紧凑的 4 个半径（左上、右上、右下、左下）。 */
	static float[] toFourCorners(float[] radii)
	{
		float[] corners = new float[4];

		if(radii == null)
			return corners;

		if(radii.length >= 8)
		{
			for(int corner = 0; corner < 4; corner++)
				corners[corner] = radii[corner * 2];
		}else
			for(int corner = 0; corner < 4 && corner < radii.length; corner++)
				corners[corner] = radii[corner];

		return corners;
	}

	static void strokeRoundRect(GuiGraphics graphics, float x, float y,
		float width, float height, float radius, int color)
	{
		if(width <= 0 || height <= 0 || color >>> 24 == 0)
			return;

		FlatRenderer.drawRoundedOutline(graphics, Math.round(x), Math.round(y),
			Math.round(x + width), Math.round(y + height), Math.round(radius),
			color);
	}

	/**
	 * 圆角 + 纵向渐变。渐变由顶点色插值完成，所以在圆角上也成立。
	 */
	static void fillVerticalGradient(GuiGraphics graphics, float x, float y,
		float width, float height, float radius, int top, int bottom)
	{
		if(width <= 0 || height <= 0)
			return;

		float r = Math.max(0, radius);
		FlatRenderer.fillRoundedRectCornersGradient(graphics, x, y, x + width,
			y + height, new float[]{r, r, r, r}, top, bottom);
	}

	/**
	 * 与 Skia 后端同样的多层圆角矩形叠法，所以两边的阴影观感一致。
	 */
	static void softShadow(GuiGraphics graphics, float x, float y, float width,
		float height, float radius, float spread, int color, int layers)
	{
		if(width <= 0 || height <= 0 || spread <= 0)
			return;

		int baseAlpha = color >>> 24;
		int rgb = color & 0xFFFFFF;

		for(int layer = layers - 1; layer >= 0; layer--)
		{
			float progress = layer / (float)layers;
			float grow = spread * (1F - progress);
			int alpha = Math.round(baseAlpha * (1F - progress)
				* (1F - progress) / layers * 3F);

			if(alpha <= 0)
				continue;

			fillRoundRect(graphics, x - grow, y - grow, width + grow * 2F,
				height + grow * 2F, radius + grow, alpha << 24 | rgb);
		}
	}

	/**
	 * 三角形按扫描线填充。几何复用 {@link TwilightGeometry#triangleSpans}，与
	 * Skia 后端同一套，所以两边画出来的图标形状一致。
	 */
	static void fillTriangle(GuiGraphics graphics, float x1, float y1, float x2,
		float y2, float x3, float y3, int color)
	{
		if(color >>> 24 == 0)
			return;

		float[] spans = new float[SPANS];
		int rows = TwilightGeometry.triangleSpans(x1, y1, x2, y2, x3, y3,
			spans);
		int top = TwilightGeometry.triangleTop(y1, y2, y3);

		for(int row = 0; row < rows; row++)
		{
			float left = spans[row * 2];
			float right = spans[row * 2 + 1];

			if(right <= left)
				continue;

			fillRect(graphics, left, top + row, right - left, 1F, color);
		}
	}

	// ------------------------------------------------------------------
	// 图标
	// ------------------------------------------------------------------

	static void playGlyph(GuiGraphics graphics, float centerX, float centerY,
		float size, int color)
	{
		float half = size / 2F;
		fillTriangle(graphics, centerX - half * 0.72F, centerY - half,
			centerX - half * 0.72F, centerY + half, centerX + half * 0.86F,
			centerY, color);
	}

	static void pauseGlyph(GuiGraphics graphics, float centerX, float centerY,
		float size, int color)
	{
		float bar = size * 0.26F;
		float height = size;
		float gap = size * 0.22F;
		fillRect(graphics, centerX - gap / 2F - bar, centerY - height / 2F, bar,
			height, color);
		fillRect(graphics, centerX + gap / 2F, centerY - height / 2F, bar, height,
			color);
	}

	static void skipGlyph(GuiGraphics graphics, float centerX, float centerY,
		float size, boolean forward, int color)
	{
		float direction = forward ? 1F : -1F;
		float half = size / 2F;
		float barWidth = size * 0.16F;

		fillTriangle(graphics, centerX - direction * half, centerY - half,
			centerX - direction * half, centerY + half,
			centerX + direction * half * 0.62F, centerY, color);
		fillRect(graphics, forward ? centerX + half * 0.68F
			: centerX - half * 0.68F - barWidth, centerY - half, barWidth, size,
			color);
	}

	// ------------------------------------------------------------------
	// 变换
	// ------------------------------------------------------------------

	static void rotate(GuiGraphics graphics, float degrees, float pivotX,
		float pivotY)
	{
		graphics.pose().translate(pivotX, pivotY, 0);
		graphics.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
		graphics.pose().translate(-pivotX, -pivotY, 0);
	}

	// ------------------------------------------------------------------
	// 文字
	// ------------------------------------------------------------------

	static void text(GuiGraphics graphics, String value, float x, float topY,
		float size, TwilightSkia.Weight weight, int color)
	{
		if(value == null || value.isEmpty())
			return;

		Font font = Minecraft.getInstance().font;
		Style style = styleFor(weight);
		float scale = scaleFor(font, size);

		graphics.pose().pushPose();
		graphics.pose().translate(x, topY, 0);
		graphics.pose().scale(scale, scale, 1);
		graphics.drawString(font, RiseFont.text(value, style), 0, 0, color,
			false);
		graphics.pose().popPose();
	}

	static void textCentered(GuiGraphics graphics, String value, float centerX,
		float topY, float size, TwilightSkia.Weight weight, int color)
	{
		float width = textWidth(value, size, weight);
		text(graphics, value, centerX - width / 2F, topY, size, weight, color);
	}

	static float textWidth(String value, float size,
		TwilightSkia.Weight weight)
	{
		if(value == null || value.isEmpty())
			return 0;

		Font font = Minecraft.getInstance().font;
		return RiseFont.width(font, value, styleFor(weight))
			* scaleFor(font, size);
	}

	static float textHeight(float size, TwilightSkia.Weight weight)
	{
		// 与 Skia 后端一样按字号算行高，调用方据此做垂直居中
		return size;
	}

	private static float scaleFor(Font font, float size)
	{
		return Math.max(0.01F, size / Math.max(1, font.lineHeight));
	}

	private static Style styleFor(TwilightSkia.Weight weight)
	{
		// wurst:rise（SF Pro Rounded）只有一个字重，provider 里带了
		// minecraft:default 回落，汉字因此落到默认字体上；半粗用原版合成加粗
		if(weight == TwilightSkia.Weight.SEMIBOLD)
			return RiseFont.STYLE.withBold(true);

		return RiseFont.STYLE;
	}
}
