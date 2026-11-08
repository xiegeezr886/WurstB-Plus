/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.nanovg;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.system.MemoryUtil;

/**
 * Android-only text backend built on the platform Skia stack
 * ({@code android.graphics.Canvas}/{@code Bitmap}/{@code Paint}).
 *
 * <p>Some Android OpenGL wrappers (notably Krypton) corrupt the main
 * framebuffer when NanoVG lazily uploads its single-channel font atlas
 * during a frame, which shows up as a completely black screen. Rasterizing
 * the glyphs through the platform renderer and uploading them as ordinary
 * RGBA images avoids that code path entirely.
 *
 * <p>All Android classes are accessed through reflection so the same jar
 * still compiles and runs on desktop JVM, where this backend reports
 * {@link #isAvailable()} == false.
 */
public final class AndroidSkiaText
{
	private static final int MAX_CACHE = 512;

	private static Boolean available;
	private static Class<?> bitmapClass;
	private static Class<?> canvasClass;
	private static Class<?> paintClass;
	private static Class<?> typefaceClass;
	private static Method createBitmap;
	private static Method getPixels;
	private static Method measureText;
	private static Method getFontMetrics;
	private static Method drawText;
	private static Method drawColor;
	private static Method drawTextWithPaint;
	private static Method setAntiAlias;
	private static Method setTextSize;
	private static Method setColor;
	private static Method setTypeface;
	private static Method createTypefaceFromFile;
	private static java.lang.reflect.Constructor<?> canvasCtor;
	private static java.lang.reflect.Constructor<?> paintCtor;
	private static Object configArgb;
	private static Object clearMode;

	private static final Map<String, Object> TYPEFACES = new HashMap<>();
	private static final Map<String, TextImage> IMAGES =
		new LinkedHashMap<>(64, 0.75F, true);

	private static boolean errorLogged;

	private AndroidSkiaText() {}

	private static final class TextImage
	{
		final int image;
		final int width;
		final int height;

		TextImage(int image, int width, int height)
		{
			this.image = image;
			this.width = width;
			this.height = height;
		}
	}

	public static boolean isAvailable()
	{
		return init();
	}

	private static synchronized boolean init()
	{
		if(available != null)
			return available;

		try
		{
			bitmapClass = load("android.graphics.Bitmap");
			canvasClass = load("android.graphics.Canvas");
			paintClass = load("android.graphics.Paint");
			typefaceClass = load("android.graphics.Typeface");
			Class<?> configClass =
				load("android.graphics.Bitmap$Config");
			Class<?> modeClass =
				load("android.graphics.PorterDuff$Mode");

			configArgb = configClass.getField("ARGB_8888").get(null);
			clearMode = modeClass.getField("CLEAR").get(null);

			createBitmap = bitmapClass.getMethod("createBitmap", int.class,
				int.class, configClass);
			canvasCtor = canvasClass.getConstructor(bitmapClass);
			paintCtor = paintClass.getConstructor();
			setAntiAlias = paintClass.getMethod("setAntiAlias",
				boolean.class);
			setTextSize = paintClass.getMethod("setTextSize", float.class);
			setColor = paintClass.getMethod("setColor", int.class);
			setTypeface = paintClass.getMethod("setTypeface",
				typefaceClass);
			measureText = paintClass.getMethod("measureText", String.class);
			getFontMetrics = paintClass.getMethod("getFontMetrics");
			drawTextWithPaint = canvasClass.getMethod("drawText",
				String.class, float.class, float.class, paintClass);
			drawColor = canvasClass.getMethod("drawColor", int.class,
				modeClass);
			getPixels = bitmapClass.getMethod("getPixels", int[].class,
				int.class, int.class, int.class, int.class, int.class,
				int.class);
			createTypefaceFromFile = typefaceClass.getMethod(
				"createFromFile", File.class);

			available = true;
			System.out.println("[HUD] Android Skia text backend ready");
		}catch(Throwable t)
		{
			available = false;
			if(!errorLogged)
			{
				errorLogged = true;
				System.err.println(
					"[HUD] Android Skia text unavailable: " + t);
			}
		}
		return available;
	}

	private static Class<?> load(String name) throws ClassNotFoundException
	{
		ClassLoader system = ClassLoader.getSystemClassLoader();
		if(system != null)
			try
			{
				return Class.forName(name, false, system);
			}catch(ClassNotFoundException ignored)
			{
				// fall through to the context loader
			}

		ClassLoader context =
			Thread.currentThread().getContextClassLoader();
		if(context != null)
			return Class.forName(name, false, context);

		return Class.forName(name);
	}

	/**
	 * Tries to draw the string via the Android Skia backend. Returns true
	 * when the draw was handled (the caller must then skip NanoVG's text).
	 */
	public static boolean draw(NanoVGContext ctx, String text, float x,
		float y, int argb, float size, HudFont font)
	{
		if(!init() || ctx.getHandle() == 0 || text == null || text.isEmpty()
			|| font == null)
			return false;

		String clean = stripFormatting(text);
		if(clean.isEmpty())
			return true;

		String key = font.getResource() + '|' + size + '|'
			+ Integer.toHexString(argb) + '|' + clean;
		TextImage cached = IMAGES.get(key);
		if(cached != null)
		{
			drawImage(ctx, cached, x, y, size);
			return true;
		}

		try
		{
			Object paint = paintCtor.newInstance();
			setAntiAlias.invoke(paint, true);
			setTextSize.invoke(paint, size);
			setColor.invoke(paint, argb);
			Object typeface = typeface(font);
			if(typeface != null)
				setTypeface.invoke(paint, typeface);

			float textWidth = (Float)measureText.invoke(paint, clean);
			Object metrics = getFontMetrics.invoke(paint);
			float ascent = metrics.getClass().getField("ascent")
				.getFloat(metrics);
			float descent = metrics.getClass().getField("descent")
				.getFloat(metrics);

			int width = Math.max(1, (int)Math.ceil(textWidth) + 2);
			int height = Math.max(1, (int)Math.ceil(descent - ascent) + 2);

			Object bitmap =
				createBitmap.invoke(null, width, height, configArgb);
			Object canvas = canvasCtor.newInstance(bitmap);
			drawColor.invoke(canvas, 0, clearMode);
			drawTextWithPaint.invoke(canvas, clean, 1F, 1F - ascent, paint);

			int[] pixels = new int[width * height];
			getPixels.invoke(bitmap, pixels, 0, width, 0, 0, width, height);

			ByteBuffer rgba = MemoryUtil.memAlloc(width * height * 4);
			try
			{
				for(int pixel : pixels)
				{
					int a = pixel >>> 24 & 0xFF;
					int r = (pixel >>> 16 & 0xFF) * a / 255;
					int g = (pixel >>> 8 & 0xFF) * a / 255;
					int b = (pixel & 0xFF) * a / 255;
					rgba.put((byte)r).put((byte)g).put((byte)b)
						.put((byte)a);
				}
				rgba.flip();
				rgba.limit();
				long address = MemoryUtil.memAddress(rgba);

				// nvgCreateImageRGBA reads a ByteBuffer, so wrap the
				// address with a fresh buffer view instead of the managed
				// one already positioned at the start.
				ByteBuffer view = MemoryUtil.memByteBuffer(address,
					width * height * 4);
				int image = NanoVG.nvgCreateImageRGBA(ctx.getHandle(), width,
					height, 0, view);
				if(image == 0)
					return false;

				TextImage entry = new TextImage(image, width, height);
				IMAGES.put(key, entry);
				while(IMAGES.size() > MAX_CACHE)
				{
					String eldest = IMAGES.keySet().iterator().next();
					IMAGES.remove(eldest);
				}
				drawImage(ctx, entry, x, y, size);
				return true;
			}finally
			{
				MemoryUtil.memFree(rgba);
			}
		}catch(Throwable t)
		{
			if(!errorLogged)
			{
				errorLogged = true;
				System.err.println("[HUD] Android Skia text failed: " + t);
				t.printStackTrace();
			}
			return false;
		}
	}

	private static void drawImage(NanoVGContext ctx, TextImage image,
		float x, float y, float size)
	{
		float top = y + size / 2F - image.height / 2F;
		ctx.drawImage(image.image, x, top, image.width, image.height);
	}

	private static Object typeface(HudFont font)
	{
		String resource = font.getResource();
		Object cached = TYPEFACES.get(resource);
		if(cached != null)
			return cached;

		try(InputStream in = AndroidSkiaText.class.getClassLoader()
			.getResourceAsStream(resource))
		{
			if(in == null)
				return null;

			File file = File.createTempFile("wurstpenguin-font-", ".ttf");
			file.deleteOnExit();
			Files.copy(in, file.toPath(),
				StandardCopyOption.REPLACE_EXISTING);
			Object typeface = createTypefaceFromFile.invoke(null, file);
			TYPEFACES.put(resource, typeface);
			return typeface;
		}catch(Throwable t)
		{
			return null;
		}
	}

	private static String stripFormatting(String text)
	{
		if(text.indexOf('\u00a7') < 0)
			return text;

		StringBuilder sb = new StringBuilder(text.length());
		for(int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if(c == '\u00a7')
			{
				i++;
				continue;
			}
			sb.append(c);
		}
		return sb.toString();
	}
}
