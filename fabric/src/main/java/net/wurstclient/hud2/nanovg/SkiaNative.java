/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.nanovg;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * Loads the prebuilt Android Skia C library (from the SkiaSharp native
 * package) through JNA and exposes a tiny probe used to verify that the
 * whole chain works inside Pojav/Zalith's JVM.
 */
public final class SkiaNative
{
	private static final String RESOURCE_DIR =
		"assets/wurstpenguin/native/arm64-v8a/";
	private static final String FONT =
		"assets/wurstpenguin/nanovg/fonts/MiSans-Regular.ttf";

	public interface Skia extends Library
	{
		Pointer sk_surface_new_raster_direct(ImageInfo info, Pointer pixels,
			NativeLong rowBytes, Pointer releaseProc, Pointer context,
			Pointer props);

		Pointer sk_surface_get_canvas(Pointer surface);

		void sk_surface_unref(Pointer surface);

		Pointer sk_paint_new();

		void sk_paint_set_antialias(Pointer paint, boolean aa);

		void sk_paint_set_color(Pointer paint, int color);

		Pointer sk_typeface_create_from_file(String path, int index);

		Pointer sk_fontmgr_create_default();

		Pointer sk_fontmgr_create_from_file(Pointer fontmgr, String path,
			int index);

		void sk_typeface_unref(Pointer typeface);

		Pointer sk_font_new_with_values(Pointer typeface, float size,
			float scaleX, float skewX);

		void sk_font_delete(Pointer font);

		void sk_canvas_draw_simple_text(Pointer canvas, Pointer text,
			NativeLong byteLength, int encoding, float x, float y,
			Pointer font, Pointer paint);

		float sk_font_measure_text(Pointer font, Pointer text,
			NativeLong byteLength, int encoding, Pointer bounds,
			Pointer paint);

		float sk_font_get_metrics(Pointer font, Pointer metrics);

		void sk_canvas_clear(Pointer canvas, int color);

		void sk_paint_delete(Pointer paint);
	}

	@Structure.FieldOrder({"colorspace", "width", "height", "colorType",
		"alphaType"})
	public static final class ImageInfo extends Structure
	{
		public Pointer colorspace;
		public int width;
		public int height;
		public int colorType;
		public int alphaType;
	}

	private static Skia skia;
	private static boolean attempted;

	private SkiaNative() {}

	public static synchronized boolean isAvailable()
	{
		if(attempted)
			return skia != null;
		attempted = true;

		try
		{
			Path dir = Files.createTempDirectory("wurst-skia");
			Path skiaLib = extract(RESOURCE_DIR + "libSkiaSharp.so", dir);

			// JNA itself is already present at runtime (Baritone brings
			// jna + jna-platform and its dispatch library is loaded before
			// this class runs), so only the Skia shared object is loaded.
			skia = Native.load(skiaLib.toString(), Skia.class);
			System.out.println("[HUD] Skia native loaded: " + skiaLib);
			return true;
		}catch(Throwable t)
		{
			System.err.println("[HUD] Skia native unavailable: " + t);
			t.printStackTrace();
			skia = null;
			return false;
		}
	}

	public static void probe()
	{
		if(!isAvailable())
			return;

		try
		{
			int width = 160;
			int height = 48;
			Memory pixels = new Memory((long)width * height * 4);
			pixels.clear();

			ImageInfo info = new ImageInfo();
			info.width = width;
			info.height = height;
			info.colorType = 4; // RGBA_8888_SK_COLORTYPE
			info.alphaType = 2; // PREMUL_SK_ALPHATYPE
			info.write();

			Pointer surface = skia.sk_surface_new_raster_direct(info, pixels,
				new NativeLong(width * 4L), null, null, null);
			if(surface == null)
			{
				System.out.println("[HUD] Skia probe: surface is null");
				return;
			}

			Pointer canvas = skia.sk_surface_get_canvas(surface);
			Pointer paint = skia.sk_paint_new();
			skia.sk_paint_set_antialias(paint, true);
			skia.sk_paint_set_color(paint, 0xFFFFFFFF);

			Path fontFile = extract(FONT, Files.createTempDirectory(
				"wurst-skia-font"));
			Pointer fontmgr = skia.sk_fontmgr_create_default();
			Pointer typeface = fontmgr == null ? null
				: skia.sk_fontmgr_create_from_file(fontmgr,
					fontFile.toString(), 0);
			System.out.println("[HUD] Skia probe: typeface=" + typeface);
			if(typeface != null)
			{
				Pointer font =
					skia.sk_font_new_with_values(typeface, 28F, 1F, 0F);
				byte[] text = "Skia OK".getBytes("UTF-8");
				Memory textPtr = new Memory(text.length);
				textPtr.write(0, text, 0, text.length);
				skia.sk_canvas_draw_simple_text(canvas, textPtr,
					new NativeLong(text.length), 0, 4F, 32F, font, paint);
				skia.sk_font_delete(font);
			}

			byte[] data = pixels.getByteArray(0, width * height * 4);
			int nonZero = 0;
			for(byte b : data)
				if(b != 0)
					nonZero++;

			skia.sk_surface_unref(surface);
			System.out.println("[HUD] Skia probe done, nonZeroBytes="
				+ nonZero);
		}catch(Throwable t)
		{
			System.err.println("[HUD] Skia probe failed: " + t);
			t.printStackTrace();
		}
	}

	/** Result of rasterizing one string through Skia. */
	public static final class Raster
	{
		public final int width;
		public final int height;
		public final byte[] rgba;
		public final float ascent;

		Raster(int width, int height, byte[] rgba, float ascent)
		{
			this.width = width;
			this.height = height;
			this.rgba = rgba;
			this.ascent = ascent;
		}
	}

	/** Rasterizes a string into premultiplied RGBA pixels. */
	public static Raster rasterize(String text, HudFont hudFont, float pxSize,
		int argb)
	{
		if(!isAvailable() || text == null || text.isEmpty()
			|| hudFont == null)
			return null;

		Pointer font = font(hudFont, pxSize);
		if(font == null)
			return null;

		try
		{
			byte[] bytes = text.getBytes("UTF-8");
			Memory textPtr = new Memory(bytes.length);
			textPtr.write(0, bytes, 0, bytes.length);

			Pointer paint = skia.sk_paint_new();
			skia.sk_paint_set_antialias(paint, true);
			skia.sk_paint_set_color(paint, argb);

			float width = skia.sk_font_measure_text(font, textPtr,
				new NativeLong(bytes.length), 0, null, paint);
			Memory metrics = new Memory(64);
			skia.sk_font_get_metrics(font, metrics);
			float ascent = metrics.getFloat(8);
			float descent = metrics.getFloat(12);

			int w = Math.max(1, (int)Math.ceil(width) + 2);
			int h = Math.max(1, (int)Math.ceil(descent - ascent) + 2);

			Memory pixels = new Memory((long)w * h * 4);
			pixels.clear();

			ImageInfo info = new ImageInfo();
			info.width = w;
			info.height = h;
			info.colorType = 4;
			info.alphaType = 2;
			info.write();

			Pointer surface = skia.sk_surface_new_raster_direct(info, pixels,
				new NativeLong(w * 4L), null, null, null);
			if(surface == null)
				return null;

			Pointer canvas = skia.sk_surface_get_canvas(surface);
			skia.sk_canvas_clear(canvas, 0);
			skia.sk_canvas_draw_simple_text(canvas, textPtr,
				new NativeLong(bytes.length), 0, 1F, 1F - ascent, font, paint);

			byte[] rgba = pixels.getByteArray(0, w * h * 4);
			skia.sk_surface_unref(surface);
			skia.sk_paint_delete(paint);
			return new Raster(w, h, rgba, ascent);
		}catch(Throwable t)
		{
			System.err.println("[HUD] Skia rasterize failed: " + t);
			return null;
		}
	}

	private static final java.util.Map<String, Pointer> TYPEFACES =
		new java.util.HashMap<>();
	private static final java.util.Map<String, Pointer> FONTS =
		new java.util.HashMap<>();

	private static Pointer font(HudFont hudFont, float pxSize)
	{
		String key = hudFont.getResource() + '@' + pxSize;
		Pointer cached = FONTS.get(key);
		if(cached != null)
			return cached;

		try
		{
			Pointer typeface = TYPEFACES.get(hudFont.getResource());
			if(typeface == null)
			{
				Path fontFile = extract(hudFont.getResource(),
					Files.createTempDirectory("wurst-skia-font"));
				Pointer mgr = skia.sk_fontmgr_create_default();
				typeface = mgr == null ? null
					: skia.sk_fontmgr_create_from_file(mgr,
						fontFile.toString(), 0);
				if(typeface == null)
					return null;
				TYPEFACES.put(hudFont.getResource(), typeface);
			}

			Pointer font = skia.sk_font_new_with_values(typeface, pxSize, 1F,
				0F);
			if(font != null)
				FONTS.put(key, font);
			return font;
		}catch(Throwable t)
		{
			return null;
		}
	}

	private static Path extract(String resource, Path dir) throws Exception
	{
		String name = resource.substring(resource.lastIndexOf('/') + 1);
		Path out = dir.resolve(name);
		try(InputStream in =
			SkiaNative.class.getClassLoader().getResourceAsStream(resource))
		{
			if(in == null)
				throw new IllegalStateException("missing resource " + resource);
			Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
		}
		return out;
	}
}
