/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.nanovg;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.system.MemoryUtil;

/**
 * Software text rasterizer used on Android GL wrappers that corrupt the
 * framebuffer when NanoVG uploads glyphs at runtime.
 *
 * <p>The Pojav/Zalith JVM cannot see {@code android.graphics.*}, so this
 * backend uses AWT/Java2D (which Pojav ships headless) to rasterize glyphs
 * into an ARGB image, then uploads the result as a regular RGBA NanoVG
 * image. Only text goes through this path; shapes still use NanoVG.
 */
public final class SoftwareText
{
	private static final int MAX_CACHE = 512;

	private static Boolean available;
	private static final Map<String, Font> FONTS = new HashMap<>();
	private static final Map<String, TextImage> IMAGES =
		new LinkedHashMap<>(64, 0.75F, true);
	private static boolean errorLogged;

	private SoftwareText() {}

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

	public static synchronized boolean isAvailable()
	{
		if(available != null)
			return available;
		try
		{
			BufferedImage probe =
				new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = probe.createGraphics();
			g.dispose();
			available = true;
			System.out.println("[HUD] Software text backend ready");
		}catch(Throwable t)
		{
			available = false;
			if(!errorLogged)
			{
				errorLogged = true;
				System.err.println(
					"[HUD] Software text backend unavailable: " + t);
			}
		}
		return available;
	}

	public static boolean draw(NanoVGContext ctx, String text, float x,
		float y, int argb, float size, HudFont font)
	{
		if(!isAvailable() || ctx.getHandle() == 0 || text == null
			|| text.isEmpty() || font == null)
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
			Font awtFont = font(font, size);
			if(awtFont == null)
				return false;

			BufferedImage probe =
				new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			Graphics2D pg = probe.createGraphics();
			pg.setFont(awtFont);
			FontMetrics metrics = pg.getFontMetrics();
			int textWidth = metrics.stringWidth(clean);
			int ascent = metrics.getAscent();
			int descent = metrics.getDescent();
			pg.dispose();

			int width = Math.max(1, textWidth + 2);
			int height = Math.max(1, ascent + descent + 2);
			BufferedImage image = new BufferedImage(width, height,
				BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setFont(awtFont);
			g.setColor(new Color(argb, true));
			g.drawString(clean, 1, 1 + ascent);
			g.dispose();

			int[] pixels = new int[width * height];
			image.getRGB(0, 0, width, height, pixels, 0, width);

			ByteBuffer rgba = MemoryUtil.memAlloc(width * height * 4);
			try
			{
				for(int pixel : pixels)
				{
					int a = pixel >>> 24 & 0xFF;
					int r = (pixel >>> 16 & 0xFF) * a / 255;
					int gg = (pixel >>> 8 & 0xFF) * a / 255;
					int b = (pixel & 0xFF) * a / 255;
					rgba.put((byte)r).put((byte)gg).put((byte)b)
						.put((byte)a);
				}
				rgba.flip();
				ByteBuffer view = MemoryUtil.memByteBuffer(
					MemoryUtil.memAddress(rgba), width * height * 4);
				int handle = NanoVG.nvgCreateImageRGBA(ctx.getHandle(),
					width, height, 0, view);
				if(handle == 0)
					return false;

				TextImage entry = new TextImage(handle, width, height);
				IMAGES.put(key, entry);
				while(IMAGES.size() > MAX_CACHE)
					IMAGES.remove(IMAGES.keySet().iterator().next());
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
				System.err.println("[HUD] Software text failed: " + t);
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

	private static Font font(HudFont font, float size)
	{
		String resource = font.getResource();
		Font base = FONTS.get(resource);
		if(base == null)
			try(InputStream in = SoftwareText.class.getClassLoader()
				.getResourceAsStream(resource))
			{
				if(in == null)
					return null;
				base = Font.createFont(Font.TRUETYPE_FONT, in);
				FONTS.put(resource, base);
			}catch(Throwable t)
			{
				return null;
			}
		return base.deriveFont(size);
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
