/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.render.skia;

import java.util.HashMap;

import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.Font;
import org.jetbrains.skia.FontMetrics;
import org.jetbrains.skia.Paint;
import org.jetbrains.skia.PaintMode;
import org.jetbrains.skia.RRect;
import org.jetbrains.skia.Rect;
import org.jetbrains.skia.Typeface;

import net.minecraft.client.gui.GuiGraphics;

/**
 * ESP 需要的绘制图元，落在本工程既有的 Skia 区域管线上。
 *
 * <p>
 * 与 {@code TwilightSkia} 一样，{@link SkiaRegionRenderer#beginRegion} 已经把
 * GUI 缩放与区域平移预置好了，所以这里所有坐标都是普通 GUI 坐标，和
 * {@code EspNameTagLayout} 算出来的几何在同一空间。管线一帧只允许一个区域，
 * 因此必须成对使用：
 *
 * <pre>
 * if(EspSkia.begin(graphics, 0, 0, width, height))
 * {
 * 	EspSkia.outlineRect(...);
 * 	EspSkia.end(graphics);
 * }
 * </pre>
 *
 * <p>
 * {@link #begin} 返回 false 时调用方必须走原版 {@code RenderUtils} 兜底；
 * 原生库没加载、以及<b>这一帧已经有别的界面开着区域</b>（例如 Twilight 音乐
 * 界面），都会返回 false——后者是因为区域管线只会复用同一个画布、不会重新
 * 施加平移，硬画进去会把别人的画面写花。
 *
 * <p>
 * 边框原语是 OpenOpal {@code NVGRenderer} 的逐条移植：
 * {@code rectOutline} 由四条填充矩形拼成边框，{@code rectOutlineStroke} 先在
 * 外扩一圈画深色描边、再在原位画彩色细线，{@code rectStroke} 先画外扩的实心
 * 色块再叠内层色块。这三者决定了参考 ESP 那个「彩色线外面包一圈黑边」的样子。
 */
public final class EspSkia
{
	private static final HashMap<String, Font> FONTS = new HashMap<>();

	private static Canvas canvas;
	private static Paint paint;

	private EspSkia()
	{
	}

	/** @return 本帧 Skia 是否可用；返回 false 时不得调用任何绘制方法。 */
	public static boolean begin(GuiGraphics graphics, int x, int y, int width,
		int height)
	{
		if(width <= 0 || height <= 0)
			return false;
		if(SkiaRegionRenderer.get().isRegionDrawing())
		{
			canvas = null;
			return false;
		}

		Canvas started =
			SkiaRegionRenderer.get().beginRegion(x, y, width, height);

		if(started == null)
		{
			canvas = null;
			return false;
		}

		canvas = started;
		return true;
	}

	/** 上传区域并 blit 回 GUI。 */
	public static void end(GuiGraphics graphics)
	{
		canvas = null;
		SkiaRegionRenderer.get().endRegion(graphics);
	}

	public static boolean isActive()
	{
		return canvas != null;
	}

	// ------------------------------------------------------------------
	// 基础图元
	// ------------------------------------------------------------------

	public static void fillRect(float x, float y, float width, float height,
		int color)
	{
		if(canvas == null || width <= 0 || height <= 0)
			return;

		canvas.drawRect(new Rect(x, y, x + width, y + height), fill(color));
	}

	public static void fillRoundRect(float x, float y, float width,
		float height, float radius, int color)
	{
		if(canvas == null || width <= 0 || height <= 0)
			return;

		canvas.drawRRect(rrect(x, y, width, height, radius), fill(color));
	}

	public static void strokeRoundRect(float x, float y, float width,
		float height, float radius, float strokeWidth, int color)
	{
		if(canvas == null || width <= 0 || height <= 0)
			return;

		Paint brush = stroke(color, strokeWidth);
		canvas.drawRRect(rrect(x, y, width, height, radius), brush);
	}

	// ------------------------------------------------------------------
	// 参考的边框组合（NVGRenderer 逐条移植）
	// ------------------------------------------------------------------

	/**
	 * {@code NVGRenderer.rectOutline}：四条填充矩形拼成一圈厚边框。注意参考
	 * 右边与下边都少画一个 thickness，所以右下角是实心的（不是四个独立矩形
	 * 拼出的空心框）。
	 */
	public static void outlineRect(float x, float y, float width, float height,
		float thickness, int color)
	{
		if(canvas == null || width <= 0 || height <= 0 || thickness <= 0)
			return;

		fillRect(x, y, width, thickness, color);
		fillRect(x + width - thickness, y + thickness, thickness,
			height - thickness, color);
		fillRect(x, y + height - thickness, width - thickness, thickness,
			color);
		fillRect(x, y + thickness, thickness, height - thickness, color);
	}

	/**
	 * {@code NVGRenderer.rectOutlineStroke}：彩色细线 + 深色描边。
	 *
	 * @param outlineThickness
	 *            彩色线的宽度（参考用 0.5）。
	 * @param casingThickness
	 *            深色描边的宽度（参考用 {@code outlineThickness * 3}）。
	 */
	public static void outlineRectCased(float x, float y, float width,
		float height, float outlineThickness, float casingThickness,
		int color, int casingColor)
	{
		if(canvas == null)
			return;

		outlineRect(x - outlineThickness, y - outlineThickness,
			width + outlineThickness * 2F, height + outlineThickness * 2F,
			casingThickness, casingColor);
		outlineRect(x, y, width, height, outlineThickness, color);
	}

	/**
	 * {@code NVGRenderer.rectStroke}：先画外扩的实心色块，再叠内层色块，
	 * 于是外层只剩一圈描边。参考的血条描边用它。
	 */
	public static void casedRect(float x, float y, float width, float height,
		float thickness, int color, int casingColor)
	{
		if(canvas == null || width <= 0 || height <= 0)
			return;

		fillRect(x - thickness, y - thickness, width + thickness * 2F,
			height + thickness * 2F, casingColor);
		fillRect(x, y, width, height, color);
	}

	// ------------------------------------------------------------------
	// 文字
	// ------------------------------------------------------------------

	/**
	 * 按<b>基线</b>绘制文本，与 NanoVG 的 {@code nvgText} 同一语义——参考的
	 * 铭牌排版正是按基线算的，所以这里不做「顶边转基线」的换算。
	 */
	public static void textBaseline(String text, float x, float baselineY,
		float size, int color)
	{
		if(canvas == null || text == null || text.isEmpty())
			return;

		canvas.drawString(text, x, baselineY, font(size), fill(color));
	}

	public static float textWidth(String text, float size)
	{
		if(text == null || text.isEmpty())
			return 0;

		return font(size).measureTextWidth(text);
	}

	/** 参考的 {@code ColorUtility.applyOpacity(int, float)}。 */
	public static int applyOpacity(int color, float factor)
	{
		float clamped = Math.min(1F, Math.max(0F, factor));
		return color & 0x00FFFFFF | (int)(clamped * 255F) << 24;
	}

	// ------------------------------------------------------------------
	// 内部
	// ------------------------------------------------------------------

	/**
	 * 懒创建：原生库不可用时构造 Skia 对象会抛异常，本类必须保持可加载
	 * （单测路径不碰这里）。
	 */
	private static Paint paint()
	{
		if(paint == null)
		{
			paint = new Paint();
			paint.setAntiAlias(true);
		}

		return paint;
	}

	private static Paint fill(int color)
	{
		Paint brush = paint();
		brush.setMode(PaintMode.FILL);
		brush.setShader(null);
		brush.setColor(color);
		return brush;
	}

	private static Paint stroke(int color, float strokeWidth)
	{
		Paint brush = paint();
		brush.setMode(PaintMode.STROKE);
		brush.setShader(null);
		brush.setColor(color);
		brush.setStrokeWidth(strokeWidth);
		return brush;
	}

	/**
	 * 铭牌参考用 product-sans-bold，本工程只有苹方三字重，取最接近的
	 * semibold。字号按 0.5 取整以复用字体对象。
	 */
	private static Font font(float size)
	{
		float rounded = Math.round(size * 2F) / 2F;
		String key = String.valueOf(rounded);
		Font cached = FONTS.get(key);

		if(cached != null)
			return cached;

		Typeface typeface = SkiaFontManager.get().semibold();
		Font created = new Font(typeface, rounded);
		FONTS.put(key, created);
		return created;
	}

	private static RRect rrect(float x, float y, float width, float height,
		float radius)
	{
		float limit = Math.min(width, height) / 2F;
		float safe = Math.max(0F, Math.min(radius, limit));
		return RRect.makeLTRB(x, y, x + width, y + height, safe);
	}
}
