/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.twilight;

import java.util.HashMap;

import org.jetbrains.skia.Canvas;
import org.jetbrains.skia.Font;
import org.jetbrains.skia.FontMetrics;
import org.jetbrains.skia.Paint;
import org.jetbrains.skia.PaintMode;
import org.jetbrains.skia.Point;
import org.jetbrains.skia.RRect;
import org.jetbrains.skia.Rect;
import org.jetbrains.skia.Shader;
import org.jetbrains.skia.Typeface;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.render.skia.SkiaFontManager;
import net.wurstclient.render.skia.SkiaRegionRenderer;

/**
 * The drawing primitives the Twilight Echo port needs, on top of the project's
 * Skia region pipeline.
 *
 * <p>
 * {@link SkiaRegionRenderer#beginRegion(int, int, int, int)} already applies the
 * GUI scale and the region translation, so every coordinate here is a plain GUI
 * coordinate - the same space {@link TwilightShellLayout} works in. The pipeline
 * allows exactly one region per frame, so this class keeps the frame state and
 * has to be used as a pair:
 *
 * <pre>
 * if(TwilightSkia.begin(graphics, 0, 0, width, height))
 * {
 * 	TwilightSkia.fillRoundRect(...);
 * 	TwilightSkia.end(graphics);
 * }
 * </pre>
 *
 * <p>
 * If the native library cannot be loaded, {@link #begin} returns false and the
 * caller has to fall back to vanilla rendering; {@link TwilightShellScreen} does
 * exactly that. All Skia objects are created lazily, because instantiating one
 * without the native library throws - the class itself must stay loadable.
 *
 * <p>
 * Every Skia signature used here was checked against skiko-awt 0.8.19 with
 * javap. Rounded rectangles are drawn by Skia itself, which is why the port does
 * not need the 16 segment triangle fan of {@code RoundedRectRenderer}.
 */
public final class TwilightSkia
{
	/** Which PingFang weight to use; the project has no other fonts. */
	public enum Weight
	{
		LIGHT,
		REGULAR,
		SEMIBOLD
	}
	
	private static final int SHADOW_LAYERS = 6;
	private static final int GLYPH_SPANS = 512;
	private static final HashMap<String, Font> FONTS = new HashMap<>();
	
	private static Canvas canvas;
	private static Paint paint;

	/** 是否走原版图元后端，由带 {@code useVanilla} 的 {@link #begin} 设定。 */
	private static boolean vanilla;
	private static GuiGraphics vanillaGraphics;

	/** 原版后端下窗口原点在屏幕上的位置：scissor 不吃 pose，裁剪得自己加上它。 */
	private static int vanillaOffsetX;
	private static int vanillaOffsetY;

	/** 原版后端下当前是否有矩形裁剪生效。 */
	private static boolean vanillaClipActive;

	/** {@link #save()} 时记下当时是否有矩形裁剪，好让 {@link #restore()} 对应地关掉。 */
	private static final java.util.ArrayDeque<Boolean> clipStack =
		new java.util.ArrayDeque<>();
	
	private TwilightSkia()
	{
		
	}
	
	/**
	 * @return whether Skia is available for this frame. When it returns false,
	 *         the caller must not call any drawing method and has to use the
	 *         vanilla fallback instead.
	 */
	public static boolean begin(GuiGraphics graphics, int x, int y, int width,
		int height)
	{
		vanilla = false;
		vanillaGraphics = null;

		if(width <= 0 || height <= 0)
			return false;

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

	/**
	 * 开始绘制，并选择用哪套后端。
	 *
	 * <p>
	 * 原版后端不再建立 Skia 表面：它把传入的窗口原点压进 pose 栈，于是调用方
	 * 仍然用 0 起算的局部坐标绘制，和区域路径的坐标语义一致。
	 *
	 * @param useVanilla
	 *            true 走原版图元，false 与旧的重载等价（ESP 仍在用 Skia）。
	 */
	public static boolean begin(GuiGraphics graphics, int x, int y, int width,
		int height, boolean useVanilla)
	{
		if(!useVanilla)
			return begin(graphics, x, y, width, height);

		if(width <= 0 || height <= 0)
			return false;

		vanilla = true;
		vanillaGraphics = graphics;
		vanillaOffsetX = x;
		vanillaOffsetY = y;
		vanillaClipActive = false;
		clipStack.clear();
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		return true;
	}

	/**
	 * Draws into a caller-owned Skia canvas instead of the per-frame region
	 * pipeline.
	 *
	 * <p>
	 * This is what lets the Twilight shell paint a cached layer: the caller
	 * rasterises the layer only when its content changes, uploads it once, and
	 * blits the texture on every other frame. The coordinates are the same GUI
	 * coordinates the region path uses, because the caller has already applied
	 * the scale and the window translation to its canvas.
	 */
	public static void bindCanvas(Canvas target)
	{
		vanilla = false;
		vanillaGraphics = null;
		canvas = target;
	}

	/** Stops drawing into a canvas bound with {@link #bindCanvas}. */
	public static void unbindCanvas()
	{
		canvas = null;
	}

	/** Whether a canvas bound with {@link #bindCanvas} is still in use. */
	public static boolean isCanvasBound()
	{
		return !vanilla && canvas != null;
	}

	/** Uploads the region and blits it back into the GUI. */
	public static void end(GuiGraphics graphics)
	{
		if(vanilla)
		{
			graphics.pose().popPose();
			vanilla = false;
			vanillaGraphics = null;
			clipStack.clear();
			return;
		}

		canvas = null;
		SkiaRegionRenderer.get().endRegion(graphics);
	}

	public static boolean isActive()
	{
		return vanilla ? vanillaGraphics != null : canvas != null;
	}
	
	// ------------------------------------------------------------------
	// 基础图元
	// ------------------------------------------------------------------
	
	public static void fillRect(float x, float y, float width, float height,
		int color)
	{
		if(vanilla)
		{
			TwilightVanilla.fillRect(vanillaGraphics, x, y, width, height,
				color);
			return;
		}

		if(canvas == null || width <= 0 || height <= 0)
			return;
		
		Paint brush = fill(color);
		canvas.drawRect(new Rect(x, y, x + width, y + height), brush);
	}
	
	public static void fillRoundRect(float x, float y, float width,
		float height, float radius, int color)
	{
		if(vanilla)
		{
			TwilightVanilla.fillRoundRect(vanillaGraphics, x, y, width, height,
				radius, color);
			return;
		}

		if(canvas == null || width <= 0 || height <= 0)
			return;
		
		canvas.drawRRect(rrect(x, y, width, height, radius), fill(color));
	}
	
	/**
	 * A rounded rectangle whose four corners differ, e.g. the reference side bar
	 * with {@code border-radius: 0 26px 26px 0}.
	 */
	public static void fillRoundRectCorners(float x, float y, float width,
		float height, float[] radii, int color)
	{
		if(vanilla)
		{
			TwilightVanilla.fillRoundRectCorners(vanillaGraphics, x, y, width,
				height, radii, color);
			return;
		}

		if(canvas == null || width <= 0 || height <= 0)
			return;
		
		canvas.drawRRect(complexRrect(x, y, width, height, radii),
			fill(color));
	}
	
	public static void strokeRoundRect(float x, float y, float width,
		float height, float radius, float strokeWidth, int color)
	{
		if(vanilla)
		{
			TwilightVanilla.strokeRoundRect(vanillaGraphics, x, y, width,
				height, radius, color);
			return;
		}

		if(canvas == null || width <= 0 || height <= 0)
			return;
		
		Paint brush = paint();
		brush.setMode(PaintMode.STROKE);
		brush.setShader(null);
		brush.setStrokeWidth(strokeWidth);
		brush.setColor(color);
		canvas.drawRRect(rrect(x, y, width, height, radius), brush);
		brush.setMode(PaintMode.FILL);
	}
	
	// ------------------------------------------------------------------
	// 渐变与阴影
	// ------------------------------------------------------------------
	
	/**
	 * The vertical gradient the reference uses for page backgrounds and hero
	 * cards. Skia also has radial and sweep gradients, the vanilla fallback does
	 * not, so only the linear one is exposed for now.
	 */
	public static void fillVerticalGradient(float x, float y, float width,
		float height, float radius, int top, int bottom)
	{
		if(vanilla)
		{
			TwilightVanilla.fillVerticalGradient(vanillaGraphics, x, y, width,
				height, radius, top, bottom);
			return;
		}

		if(canvas == null || width <= 0 || height <= 0)
			return;
		
		Shader shader = Shader.Companion.makeLinearGradient(new Point(0, y),
			new Point(0, y + height), new int[]{top, bottom});
		
		Paint brush = paint();
		brush.setMode(PaintMode.FILL);
		brush.setShader(shader);
		brush.setColor(0xFFFFFFFF);
		canvas.drawRRect(rrect(x, y, width, height, radius), brush);
		brush.setShader(null);
	}
	
	/**
	 * A soft shadow. Skia's canvas only offers rectangular shadows, while the
	 * reference uses multi layer box shadows such as {@code 12px 22px 58px}, so
	 * this stacks rounded rectangles with decreasing alpha - the same approach
	 * {@code RiseFrostedGlass} uses for the same reason.
	 */
	public static void softShadow(float x, float y, float width, float height,
		float radius, float spread, int color)
	{
		if(vanilla)
		{
			TwilightVanilla.softShadow(vanillaGraphics, x, y, width, height,
				radius, spread, color, SHADOW_LAYERS);
			return;
		}

		if(canvas == null || width <= 0 || height <= 0 || spread <= 0)
			return;
		
		int baseAlpha = color >>> 24;
		int rgb = color & 0xFFFFFF;
		
		for(int layer = SHADOW_LAYERS - 1; layer >= 0; layer--)
		{
			float progress = layer / (float)SHADOW_LAYERS;
			float grow = spread * (1F - progress);
			int alpha = Math.round(baseAlpha * (1F - progress) * (1F - progress)
				/ SHADOW_LAYERS * 3F);
			
			if(alpha <= 0)
				continue;
			
			fillRoundRect(x - grow, y - grow, width + grow * 2F,
				height + grow * 2F, radius + grow, alpha << 24 | rgb);
		}
	}
	
	// ------------------------------------------------------------------
	// 裁剪
	// ------------------------------------------------------------------
	
	/** Clips to a rounded rectangle; pair every call with {@link #restore()}. */
	public static void clipRoundRect(float x, float y, float width,
		float height, float radius)
	{
		if(vanilla)
		{
			// 原版只有矩形裁剪，圆角裁剪退化成矩形裁剪
			vanillaGraphics.enableScissor(
				Math.round(vanillaOffsetX + x),
				Math.round(vanillaOffsetY + y),
				Math.round(vanillaOffsetX + x + width),
				Math.round(vanillaOffsetY + y + height));
			vanillaClipActive = true;
			return;
		}

		if(canvas == null)
			return;
		
		canvas.save();
		canvas.clipRRect(rrect(x, y, width, height, radius));
	}
	
	/** Saves the canvas state; pair it with {@link #restore()}. */
	public static void save()
	{
		if(vanilla)
		{
			vanillaGraphics.pose().pushPose();
			clipStack.push(vanillaClipActive);
			return;
		}

		if(canvas == null)
			return;

		canvas.save();
	}
	
	/** Rotates around a pivot, used by the tilted hero covers. */
	public static void rotate(float degrees, float pivotX, float pivotY)
	{
		if(vanilla)
		{
			TwilightVanilla.rotate(vanillaGraphics, degrees, pivotX, pivotY);
			return;
		}

		if(canvas == null)
			return;

		canvas.rotate(degrees, pivotX, pivotY);
	}
	
	public static void restore()
	{
		if(vanilla)
		{
			// 没有配对的 save 就不动 pose，否则会把 begin 压进去的窗口原点弹掉
			if(clipStack.isEmpty())
				return;

			clipStack.pop();

			// scissor 不属于 pose 栈，裁剪要在这里显式收掉。判据必须是**当前**
			// 状态而不是 save 当时的状态：save 常常发生在裁剪之前，照当时的
			// 状态判断就永远关不掉，后面整屏都会被裁黑。
			if(vanillaClipActive)
			{
				vanillaGraphics.disableScissor();
				vanillaClipActive = false;
			}

			vanillaGraphics.pose().popPose();
			return;
		}

		if(canvas == null)
			return;

		canvas.restore();
	}
	
	// ------------------------------------------------------------------
	// 图标图元（参考的图标字体无法移植，见保真度文档 G9）
	// ------------------------------------------------------------------
	
	public static void fillTriangle(float x1, float y1, float x2, float y2,
		float x3, float y3, int color)
	{
		if(vanilla)
		{
			TwilightVanilla.fillTriangle(vanillaGraphics, x1, y1, x2, y2, x3,
				y3, color);
			return;
		}

		if(canvas == null)
			return;
		
		float[] spans = new float[GLYPH_SPANS];
		int rows = TwilightGeometry.triangleSpans(x1, y1, x2, y2, x3, y3,
			spans);
		int top = TwilightGeometry.triangleTop(y1, y2, y3);
		
		for(int row = 0; row < rows; row++)
		{
			float left = spans[row * 2];
			float right = spans[row * 2 + 1];
			
			if(right <= left)
				continue;
			
			fillRect(left, top + row, right - left, 1F, color);
		}
	}
	
	/** A play glyph, centred in the given box. */
	public static void playGlyph(float centerX, float centerY, float size,
		int color)
	{
		float half = size / 2F;
		fillTriangle(centerX - half * 0.75F, centerY - half, centerX + half,
			centerY, centerX - half * 0.75F, centerY + half, color);
	}
	
	/** A pause glyph, centred in the given box. */
	public static void pauseGlyph(float centerX, float centerY, float size,
		int color)
	{
		float barWidth = Math.max(1.5F, size * 0.28F);
		float half = size / 2F;
		fillRect(centerX - barWidth - size * 0.08F, centerY - half, barWidth,
			size, color);
		fillRect(centerX + size * 0.08F, centerY - half, barWidth, size, color);
	}
	
	/** A skip glyph; {@code forward = false} mirrors it for the previous track. */
	public static void skipGlyph(float centerX, float centerY, float size,
		boolean forward, int color)
	{
		float half = size / 2F;
		float direction = forward ? 1F : -1F;

		fillTriangle(centerX - direction * half, centerY - half,
			centerX + direction * half * 0.2F, centerY,
			centerX - direction * half, centerY + half, color);
		fillRect(forward ? centerX + half * 0.35F : centerX - half * 0.75F,
			centerY - half, Math.max(1.5F, size * 0.18F), size, color);
	}

	/**
	 * 一个右箭头。参考的 {@code .duo-arrow} 用的是箭头图标，而不是跳转曲目图标。
	 *
	 * <p>
	 * 由矩形与三角形拼出来，因此两条后端都自动可用（它们本来就分别路由
	 * {@link #fillRect} 与 {@link #fillTriangle}）。
	 */
	public static void arrowRightGlyph(float centerX, float centerY, float size,
		int color)
	{
		float half = size / 2F;
		float shaftHeight = Math.max(1.5F, size * 0.14F);
		float headSize = size * 0.5F;

		fillRect(centerX - half, centerY - shaftHeight / 2F,
			size - headSize * 0.4F, shaftHeight, color);
		fillTriangle(centerX + half - headSize, centerY - headSize / 2F,
			centerX + half, centerY, centerX + half - headSize,
			centerY + headSize / 2F, color);
	}
	
	/**
	 * 播放模式图标：列表循环 / 单曲循环 / 随机。
	 *
	 * <p>
	 * 参考的这三个图标来自图标字体，我们没有字形，就用圆环描边加中心记号拼出来：
	 * 列表循环是环内三角、单曲循环是环内竖杠、随机是环内交叉线，三者一眼可分。
	 * 全部由 {@link #strokeRoundRect}、{@link #fillRect} 与 {@link #fillTriangle}
	 * 组成，所以两条后端都自动可用。
	 *
	 * @param modeOrdinal
	 *            {@code NeteaseMusicPlayer.PlaybackMode} 的序数
	 */
	public static void playModeGlyph(float centerX, float centerY, float size,
		int modeOrdinal, int color)
	{
		float half = size / 2F;
		float thickness = Math.max(1.2F, size * 0.09F);

		strokeRoundRect(centerX - half, centerY - half, size, size,
			half * 0.55F, thickness, color);

		switch(modeOrdinal)
		{
			case 1 ->
			// 单曲循环：环内一根竖杠
			fillRect(centerX - thickness / 2F, centerY - half * 0.45F,
				thickness, half * 0.9F, color);

			case 2 ->
			// 随机：环内交叉线
			{
				save();
				rotate(45F, centerX, centerY);
				fillRect(centerX - half * 0.45F, centerY - thickness / 2F,
					half * 0.9F, thickness, color);
				restore();
				save();
				rotate(-45F, centerX, centerY);
				fillRect(centerX - half * 0.45F, centerY - thickness / 2F,
					half * 0.9F, thickness, color);
				restore();
			}

			default ->
			// 列表循环：环内一个小三角
			fillTriangle(centerX - half * 0.3F, centerY - half * 0.4F,
				centerX + half * 0.4F, centerY,
				centerX - half * 0.3F, centerY + half * 0.4F, color);
		}
	}

	// ------------------------------------------------------------------
	// 文字
	// ------------------------------------------------------------------
	
	/**
	 * Draws text with its <b>top</b> edge at {@code topY}; Skia wants the
	 * baseline, so the ascent of the font is subtracted.
	 */
	public static void text(String text, float x, float topY, float size,
		Weight weight, int color)
	{
		if(text == null || text.isEmpty())
			return;

		if(vanilla)
		{
			TwilightVanilla.text(vanillaGraphics, text, x, topY, size, weight,
				color);
			return;
		}

		if(canvas == null)
			return;
		
		Font font = font(size, weight);
		FontMetrics metrics = font.getMetrics();
		
		Paint brush = fill(color);
		canvas.drawString(text, x, topY - metrics.getAscent(), font, brush);
	}
	
	public static void textCentered(String text, float centerX, float topY,
		float size, Weight weight, int color)
	{
		text(text, centerX - textWidth(text, size, weight) / 2F, topY, size,
			weight, color);
	}
	
	public static float textWidth(String text, float size, Weight weight)
	{
		if(text == null || text.isEmpty())
			return 0;

		if(vanilla)
			return TwilightVanilla.textWidth(text, size, weight);

		return font(size, weight).measureTextWidth(text);
	}

	public static float textHeight(float size, Weight weight)
	{
		if(vanilla)
			return TwilightVanilla.textHeight(size, weight);

		return font(size, weight).getMetrics().getHeight();
	}
	
	// ------------------------------------------------------------------
	// 内部
	// ------------------------------------------------------------------
	
	/**
	 * Lazily created, because constructing a Skia object fails when the native
	 * library is unavailable - the class must stay loadable in unit tests.
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
	
	private static Font font(float size, Weight weight)
	{
		float rounded = Math.round(size * 2F) / 2F;
		String key = weight + "@" + rounded;
		Font cached = FONTS.get(key);
		
		if(cached != null)
			return cached;
		
		SkiaFontManager fonts = SkiaFontManager.get();
		Typeface typeface;
		
		switch(weight)
		{
			case LIGHT:
			typeface = fonts.light();
			break;
			
			case SEMIBOLD:
			typeface = fonts.semibold();
			break;
			
			default:
			typeface = fonts.regular();
			break;
		}
		
		Font font = new Font(typeface, rounded);
		FONTS.put(key, font);
		return font;
	}
	
	private static RRect rrect(float x, float y, float width, float height,
		float radius)
	{
		float limit = Math.min(width, height) / 2F;
		float safe = Math.max(0F, Math.min(radius, limit));
		return RRect.makeLTRB(x, y, x + width, y + height, safe);
	}
	
	private static RRect complexRrect(float x, float y, float width,
		float height, float[] radii)
	{
		float limit = Math.min(width, height) / 2F;
		float[] safe = new float[8];
		
		for(int i = 0; i < 8 && i < radii.length; i++)
			safe[i] = Math.max(0F, Math.min(radii[i], limit));
		
		return RRect.makeComplexLTRB(x, y, x + width, y + height, safe);
	}
}
