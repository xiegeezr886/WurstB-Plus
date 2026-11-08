/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.nanovg;

import com.sun.jna.Memory;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;

/**
 * Full-screen Skia raster surface used for the in-game HUD on Android.
 *
 * <p>Drawing happens in GUI-scaled coordinates, matching NanoVG's
 * {@code nvgScale(guiScale)} convention, so HUD module code does not need
 * to change.
 */
public final class SkiaHudSurface
{
	private static final SkiaHudSurface INSTANCE = new SkiaHudSurface();

	private int width = -1;
	private int height = -1;
	private Memory pixels;
	private Pointer surface;
	private Pointer canvas;
	private boolean active;

	private SkiaHudSurface() {}

	public static SkiaHudSurface get()
	{
		return INSTANCE;
	}

	public static boolean isAvailable()
	{
		return SkiaNative.isAvailable();
	}

	public boolean ensure(int w, int h)
	{
		if(w <= 0 || h <= 0 || SkiaNative.library() == null)
			return false;
		if(surface != null && w == width && h == height)
			return true;

		release();
		pixels = new Memory((long)w * h * 4);
		pixels.clear();

		SkiaNative.ImageInfo info = new SkiaNative.ImageInfo();
		info.width = w;
		info.height = h;
		info.colorType = 4; // RGBA_8888
		info.alphaType = 2; // PREMUL
		info.write();

		surface = SkiaNative.library().sk_surface_new_raster_direct(info,
			pixels, new NativeLong(w * 4L), null, null, null);
		if(surface == null)
			return false;

		canvas = SkiaNative.library().sk_surface_get_canvas(surface);
		width = w;
		height = h;
		return canvas != null;
	}

	public void begin()
	{
		if(canvas == null)
			return;
		SkiaNative.library().sk_canvas_reset_matrix(canvas);
		SkiaNative.library().sk_canvas_clear(canvas, 0);
		active = true;
	}

	public void end()
	{
		active = false;
	}

	public boolean isActive()
	{
		return active && canvas != null;
	}

	public Pointer canvas()
	{
		return canvas;
	}

	public byte[] pixels()
	{
		if(pixels == null)
			return null;
		return pixels.getByteArray(0, width * height * 4);
	}

	public int width()
	{
		return width;
	}

	public int height()
	{
		return height;
	}

	public void fillRect(float x, float y, float w, float h, int argb)
	{
		Pointer paint = SkiaNative.newPaint(argb, 0, 0);
		if(paint == null)
			return;
		SkiaNative.Rect rect = rect(x, y, x + w, y + h);
		SkiaNative.library().sk_canvas_draw_rect(canvas, rect, paint);
		SkiaNative.library().sk_paint_delete(paint);
	}

	public void fillRoundRect(float x, float y, float w, float h,
		float radius, int argb)
	{
		drawRoundRect(x, y, w, h, radius, argb, 0, false);
	}

	public void strokeRoundRect(float x, float y, float w, float h,
		float radius, float strokeWidth, int argb)
	{
		drawRoundRect(x, y, w, h, radius, argb, strokeWidth, true);
	}

	private void drawRoundRect(float x, float y, float w, float h,
		float radius, int argb, float strokeWidth, boolean stroke)
	{
		Pointer paint = SkiaNative.newPaint(argb, stroke ? 1 : 0, strokeWidth);
		if(paint == null)
			return;

		Pointer rrect = SkiaNative.library().sk_rrect_new();
		SkiaNative.Rect rect = rect(x, y, x + w, y + h);
		Memory radii = new Memory(4L * 2 * 4);
		for(int i = 0; i < 4; i++)
		{
			radii.setFloat(i * 8L, radius);
			radii.setFloat(i * 8L + 4, radius);
		}
		SkiaNative.library().sk_rrect_set_rect_radii(rrect, rect, radii);
		SkiaNative.library().sk_canvas_draw_rrect(canvas, rrect, paint);
		SkiaNative.library().sk_rrect_delete(rrect);
		SkiaNative.library().sk_paint_delete(paint);
	}

	public void drawCircle(float cx, float cy, float radius, int argb)
	{
		Pointer paint = SkiaNative.newPaint(argb, 0, 0);
		if(paint == null)
			return;
		SkiaNative.library().sk_canvas_draw_circle(canvas, cx, cy, radius,
			paint);
		SkiaNative.library().sk_paint_delete(paint);
	}

	public void drawLine(float x0, float y0, float x1, float y1,
		float width, int argb)
	{
		Pointer paint = SkiaNative.newPaint(argb, 1, width);
		if(paint == null)
			return;
		SkiaNative.library().sk_canvas_draw_line(canvas, x0, y0, x1, y1,
			paint);
		SkiaNative.library().sk_paint_delete(paint);
	}

	public void drawText(String text, float x, float y, float size,
		int argb, HudFont font)
	{
		SkiaNative.drawText(canvas, text, font, size, argb, x, y);
	}

	private static SkiaNative.Rect rect(float left, float top, float right,
		float bottom)
	{
		SkiaNative.Rect rect = new SkiaNative.Rect();
		rect.left = left;
		rect.top = top;
		rect.right = right;
		rect.bottom = bottom;
		rect.write();
		return rect;
	}

	private void release()
	{
		if(surface != null)
		{
			SkiaNative.library().sk_surface_unref(surface);
			surface = null;
			canvas = null;
		}
		pixels = null;
		width = -1;
		height = -1;
	}
}
