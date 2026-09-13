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
	
	/** Uploads the region and blits it back into the GUI. */
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
		
		Paint brush = fill(color);
		canvas.drawRect(new Rect(x, y, x + width, y + height), brush);
	}
	
	public static void fillRoundRect(float x, float y, float width,
		float height, float radius, int color)
	{
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
		if(canvas == null || width <= 0 || height <= 0)
			return;
		
		canvas.drawRRect(complexRrect(x, y, width, height, radii),
			fill(color));
	}
	
	public static void strokeRoundRect(float x, float y, float width,
		float height, float radius, float strokeWidth, int color)
	{
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
		if(canvas == null)
			return;
		
		canvas.save();
		canvas.clipRRect(rrect(x, y, width, height, radius));
	}
	
	/** Saves the canvas state; pair it with {@link #restore()}. */
	public static void save()
	{
		if(canvas == null)
			return;
		
		canvas.save();
	}
	
	/** Rotates around a pivot, used by the tilted hero covers. */
	public static void rotate(float degrees, float pivotX, float pivotY)
	{
		if(canvas == null)
			return;
		
		canvas.rotate(degrees, pivotX, pivotY);
	}
	
	public static void restore()
	{
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
		if(canvas == null || text == null || text.isEmpty())
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
		
		return font(size, weight).measureTextWidth(text);
	}
	
	public static float textHeight(float size, Weight weight)
	{
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
