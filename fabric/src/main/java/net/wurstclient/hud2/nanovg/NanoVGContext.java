/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.nanovg;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGLUFramebuffer;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.nanovg.NanoVG;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import net.minecraft.client.Minecraft;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.compat.HudTextures;

/**
 * NanoVG drawing context and offscreen framebuffer.
 *
 * <p>The drawing API is ported from Soar Client v7.1.2
 * ({@code me.eldodebug.soar.management.nanovg.NanoVGManager}). The original
 * Soar code draws directly into Minecraft 1.8.9's framebuffer; this version
 * renders into a dedicated NanoVG framebuffer first so that the modern
 * Minecraft render pipeline never sees interrupted state.
 */
public final class NanoVGContext
{
	private static final int MAX_COLORS = 1024;
	private static final String[] FALLBACK_FONT_PATHS = {
		"C:/Windows/Fonts/seguisym.ttf", "C:/Windows/Fonts/SEGUISYM.TTF",
		"/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
		"/usr/share/fonts/TTF/DejaVuSans.ttf",
		"/usr/share/fonts/truetype/noto/NotoSansSymbols2-Regular.ttf",
		"/System/Library/Fonts/Apple Symbols.ttf",
		"/System/Library/Fonts/Supplemental/Apple Symbols.ttf"};
	private static NanoVGContext instance;

	private long vg;
	private NVGLUFramebuffer framebuffer;
	private int framebufferWidth;
	private int framebufferHeight;
	private float guiScale = 1;
	private boolean available;
	private boolean frameOpen;
	private boolean loggedFailure;

	private final Map<String, HudFont> fonts = new HashMap<>();
	private final Map<Integer, NVGColor> colors = new HashMap<>();
	private ByteBuffer fallbackFontBuffer;
	private final Map<String, Integer> images = new HashMap<>();
	private final Map<String, Long> imageTimestamps = new HashMap<>();
	private final Map<Long, Integer> textureImages = new HashMap<>();
	private NVGPaint scratchPaint;

	private NanoVGContext() {}

	public static synchronized NanoVGContext get()
	{
		if(instance == null)
			instance = new NanoVGContext();
		return instance;
	}

	public boolean isAvailable()
	{
		return available;
	}

	public long getHandle()
	{
		return vg;
	}

	public long getContext()
	{
		return vg;
	}

	public boolean isFrameOpen()
	{
		return frameOpen;
	}

	/**
	 * Creates the NanoVG context. Must be called on the render thread.
	 */
	public boolean create()
	{
		if(available)
			return true;

		if(!NanoVGNativeLoader.ensureLoaded())
		{
			logFailure("native library unavailable");
			return false;
		}

		try
		{
			// Create LWJGL structs inside the try block: a mismatched LWJGL
			// core/nanovg pair throws NoSuchMethodError here, which must
			// disable the HUD instead of crashing during class initialisation.
			if(scratchPaint == null)
				scratchPaint = NVGPaint.create();

			vg = NanoVGGL3.nvgCreate(
				NanoVGGL3.NVG_ANTIALIAS | NanoVGGL3.NVG_STENCIL_STROKES);
			if(vg == 0)
			{
				logFailure("nvgCreateGL3 returned 0");
				return false;
			}

			for(HudFont font : HudFonts.all())
				loadFont(font);
			loadFallbackFont();

			if(!ensureFramebuffer())
				return false;

			available = true;
			System.out.println("[HUD] NanoVG GL3 context created");
			return true;
		}catch(Throwable t)
		{
			logFailure(t.toString());
			t.printStackTrace();
			destroy();
			return false;
		}
	}

	private void logFailure(String reason)
	{
		if(loggedFailure)
			return;
		loggedFailure = true;
		System.err.println("[HUD] NanoVG disabled: " + reason);
	}

	private void loadFont(HudFont font)
	{
		try(InputStream in = resource(font.getResource()))
		{
			if(in == null)
			{
				System.err.println("[HUD] Missing font " + font.getResource());
				return;
			}

			ByteBuffer buffer = readDirect(in);
			int handle = createFontMem(font.getName(), buffer);
			if(handle == -1)
			{
				System.err.println("[HUD] Could not load font "
					+ font.getResource());
				MemoryUtil.memFree(buffer);
				return;
			}

			font.setBuffer(buffer);
			font.setId(handle);
			font.setLoaded(true);
			fonts.put(font.getName(), font);
		}catch(Exception e)
		{
			System.err.println("[HUD] Could not read font "
				+ font.getResource());
			e.printStackTrace();
		}
	}

	private int createFontMem(String name, ByteBuffer buffer) throws Exception
	{
		try
		{
			Method method = NanoVG.class.getMethod("nvgCreateFontMem",
				long.class, CharSequence.class, ByteBuffer.class,
				boolean.class);
			return (int)method.invoke(null, vg, name, buffer, false);
		}catch(NoSuchMethodException e)
		{
			Method method = NanoVG.class.getMethod("nvgCreateFontMem",
				long.class, CharSequence.class, ByteBuffer.class, int.class);
			return (int)method.invoke(null, vg, name, buffer, 0);
		}
	}

	/**
	 * Loads an optional system symbol font so glyphs that MiSans does not
	 * contain (server scoreboards often use arrows, stars and other symbols)
	 * still render instead of disappearing.
	 */
	private void loadFallbackFont()
	{
		ByteBuffer buffer = null;
		for(String path : FALLBACK_FONT_PATHS)
		{
			File file = new File(path);
			if(!file.isFile())
				continue;

			try
			{
				byte[] bytes = Files.readAllBytes(file.toPath());
				buffer = MemoryUtil.memAlloc(bytes.length);
				buffer.put(bytes);
				buffer.flip();
				break;
			}catch(Exception e)
			{
				if(buffer != null)
				{
					MemoryUtil.memFree(buffer);
					buffer = null;
				}
			}
		}

		if(buffer == null)
			return;

		try
		{
			int id = createFontMem("wurst-symbol-fallback", buffer);
			if(id == -1)
			{
				MemoryUtil.memFree(buffer);
				return;
			}

			fallbackFontBuffer = buffer;
			for(HudFont font : HudFonts.all())
				if(font.isLoaded() && font.getId() != -1)
					NanoVG.nvgAddFallbackFontId(vg, font.getId(), id);
			System.out.println("[HUD] Loaded system symbol fallback font");
		}catch(Exception e)
		{
			MemoryUtil.memFree(buffer);
			System.err.println("[HUD] Could not load the symbol fallback font");
		}
	}

	private InputStream resource(String path)
	{
		ClassLoader loader = NanoVGContext.class.getClassLoader();
		InputStream in = loader.getResourceAsStream(path);
		if(in != null)
			return in;
		return loader.getResourceAsStream("/" + path);
	}

	private ByteBuffer readDirect(InputStream in) throws Exception
	{
		byte[] bytes = in.readAllBytes();
		ByteBuffer buffer = MemoryUtil.memAlloc(bytes.length);
		buffer.put(bytes);
		buffer.flip();
		return buffer;
	}

	private boolean ensureFramebuffer()
	{
		if(vg == 0 || WurstClient.MC == null)
			return false;

		Minecraft mc = WurstClient.MC;
		int width = mc.getWindow().getWidth();
		int height = mc.getWindow().getHeight();
		float scale = (float)mc.getWindow().getGuiScale();
		if(scale <= 0)
			scale = 1;

		if(framebuffer != null && width == framebufferWidth
			&& height == framebufferHeight && scale == guiScale)
			return true;

		if(framebuffer != null)
		{
			if(framebuffer.image() != 0)
				NanoVG.nvgDeleteImage(vg, framebuffer.image());
			NanoVGGL3.nvgluDeleteFramebuffer(vg, framebuffer);
			framebuffer = null;
		}

		framebuffer = NanoVGGL3.nvgluCreateFramebuffer(vg, width, height, 0);
		if(framebuffer == null || framebuffer.fbo() == 0)
		{
			framebuffer = null;
			logFailure("could not create the offscreen framebuffer");
			return false;
		}

		framebufferWidth = width;
		framebufferHeight = height;
		guiScale = scale;
		return true;
	}

	public boolean beginFrame()
	{
		if(!create() || !ensureFramebuffer())
			return false;

		NanoVGGL3.nvgluBindFramebuffer(vg, framebuffer);
		GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer.fbo());
		GL11.glViewport(0, 0, framebufferWidth, framebufferHeight);
		GL11.glDisable(GL11.GL_SCISSOR_TEST);
		GL11.glColorMask(true, true, true, true);
		GL11.glClearColor(0, 0, 0, 0);
		GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT
			| GL11.GL_STENCIL_BUFFER_BIT);

		NanoVG.nvgBeginFrame(vg, framebufferWidth, framebufferHeight, 1F);
		NanoVG.nvgScale(vg, guiScale, guiScale);
		frameOpen = true;
		return true;
	}

	public void endFrame()
	{
		if(!frameOpen)
			return;
		frameOpen = false;
		NanoVG.nvgEndFrame(vg);
		NanoVGGL3.nvgluBindFramebuffer(vg, null);
	}

	/**
	 * Starts a NanoVG frame that draws directly into the currently bound
	 * Minecraft framebuffer. Used by the ClickGUI and other screens.
	 */
	public boolean beginImmediateFrame()
	{
		if(!create() || WurstClient.MC == null)
			return false;

		int width = WurstClient.MC.getWindow().getWidth();
		int height = WurstClient.MC.getWindow().getHeight();
		float scale = (float)WurstClient.MC.getWindow().getGuiScale();
		if(scale <= 0)
			scale = 1;

		NanoVG.nvgBeginFrame(vg, width, height, 1F);
		NanoVG.nvgScale(vg, scale, scale);
		frameOpen = true;
		return true;
	}

	public void endImmediateFrame()
	{
		if(!frameOpen)
			return;
		frameOpen = false;
		NanoVG.nvgEndFrame(vg);
	}

	/**
	 * Draws the offscreen HUD onto the currently bound Minecraft target.
	 */
	public void composite()
	{
		if(!available || framebuffer == null || framebuffer.image() == 0)
			return;

		NanoVG.nvgBeginFrame(vg, framebufferWidth, framebufferHeight, 1F);
		NVGPaint paint = scratchPaint;
		try
		{
			NanoVG.nvgImagePattern(vg, 0, 0, framebufferWidth,
				framebufferHeight, 0, framebuffer.image(), 1F, paint);
			NanoVG.nvgBeginPath(vg);
			NanoVG.nvgRect(vg, 0, 0, framebufferWidth, framebufferHeight);
			NanoVG.nvgFillPaint(vg, paint);
			NanoVG.nvgFill(vg);
		}finally
		{
			NanoVG.nvgEndFrame(vg);
		}
	}

	public float getGuiScale()
	{
		return guiScale;
	}

	public int getFramebufferWidth()
	{
		return framebufferWidth;
	}

	public int getFramebufferHeight()
	{
		return framebufferHeight;
	}

	// ------------------------------------------------------------- primitives
	public void drawRect(float x, float y, float width, float height,
		Color color)
	{
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgRect(vg, x, y, width, height);
		NanoVG.nvgFillColor(vg, getColor(color));
		NanoVG.nvgFill(vg);
	}

	public void drawRect(float x, float y, float width, float height,
		int argb)
	{
		if(width <= 0 || height <= 0 || argb >>> 24 == 0)
			return;
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgRect(vg, x, y, width, height);
		NanoVG.nvgFillColor(vg, getColor(argb));
		NanoVG.nvgFill(vg);
	}

	public void drawRoundedRect(float x, float y, float width, float height,
		float radius, int argb)
	{
		if(width < 0 || height < 0 || argb >>> 24 == 0)
			return;
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
		NanoVG.nvgFillColor(vg, getColor(argb));
		NanoVG.nvgFill(vg);
	}

	public void drawOutlineRoundedRect(float x, float y, float width,
		float height, float radius, float strokeWidth, int argb)
	{
		if(width < 0 || height < 0 || argb >>> 24 == 0)
			return;
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
		NanoVG.nvgStrokeWidth(vg, strokeWidth);
		NanoVG.nvgStrokeColor(vg, getColor(argb));
		NanoVG.nvgStroke(vg);
	}

	public void drawVerticalGradient(float x, float y, float width,
		float height, int topArgb, int bottomArgb)
	{
		if(width <= 0 || height <= 0)
			return;
		NVGPaint paint = scratchPaint;
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgRect(vg, x, y, width, height);
		NanoVG.nvgFillPaint(vg, NanoVG.nvgLinearGradient(vg, x, y, x,
			y + height, getColor(topArgb), getColor(bottomArgb), paint));
		NanoVG.nvgFill(vg);
	}

	public void drawHorizontalGradient(float x, float y, float width,
		float height, int leftArgb, int rightArgb)
	{
		if(width <= 0 || height <= 0)
			return;
		NVGPaint paint = scratchPaint;
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgRect(vg, x, y, width, height);
		NanoVG.nvgFillPaint(vg, NanoVG.nvgLinearGradient(vg, x, y,
			x + width, y, getColor(leftArgb), getColor(rightArgb), paint));
		NanoVG.nvgFill(vg);
	}

	public void rotateRadians(float radians)
	{
		NanoVG.nvgRotate(vg, radians);
	}

	public void drawCircle(float x, float y, float radius, int argb)
	{
		if(radius <= 0 || argb >>> 24 == 0)
			return;
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgCircle(vg, x, y, radius);
		NanoVG.nvgFillColor(vg, getColor(argb));
		NanoVG.nvgFill(vg);
	}

	public void drawLine(float x1, float y1, float x2, float y2,
		float strokeWidth, int argb)
	{
		if(argb >>> 24 == 0)
			return;
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgMoveTo(vg, x1, y1);
		NanoVG.nvgLineTo(vg, x2, y2);
		NanoVG.nvgStrokeWidth(vg, strokeWidth);
		NanoVG.nvgStrokeColor(vg, getColor(argb));
		NanoVG.nvgStroke(vg);
	}

	public void drawPolyline(float[][] points, int count, float strokeWidth,
		int argb)
	{
		if(points == null || count < 2 || argb >>> 24 == 0)
			return;
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgMoveTo(vg, points[0][0], points[0][1]);
		for(int i = 1; i < count; i++)
			NanoVG.nvgLineTo(vg, points[i][0], points[i][1]);
		NanoVG.nvgStrokeWidth(vg, strokeWidth);
		NanoVG.nvgStrokeColor(vg, getColor(argb));
		NanoVG.nvgStroke(vg);
	}

	public void drawClosedPolyline(float[][] points, int count,
		float strokeWidth, int argb)
	{
		if(points == null || count < 2 || argb >>> 24 == 0)
			return;
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgMoveTo(vg, points[0][0], points[0][1]);
		for(int i = 1; i < count; i++)
			NanoVG.nvgLineTo(vg, points[i][0], points[i][1]);
		NanoVG.nvgClosePath(vg);
		NanoVG.nvgStrokeWidth(vg, strokeWidth);
		NanoVG.nvgStrokeColor(vg, getColor(argb));
		NanoVG.nvgStroke(vg);
	}

	public void fillPolygon(float[][] points, int count, int argb)
	{
		if(points == null || count < 3 || argb >>> 24 == 0)
			return;
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgMoveTo(vg, points[0][0], points[0][1]);
		for(int i = 1; i < count; i++)
			NanoVG.nvgLineTo(vg, points[i][0], points[i][1]);
		NanoVG.nvgClosePath(vg);
		NanoVG.nvgFillColor(vg, getColor(argb));
		NanoVG.nvgFill(vg);
	}

	/**
	 * Draws a texture with an ARGB tint. The tint is multiplied into the
	 * texture, matching the old {@code GuiGraphics.setColor()} + {@code blit()}
	 * behaviour.
	 */
	public void drawImage(int texture, float x, float y, float width,
		float height, float radius, int tint)
	{
		int image = textureImage(texture, width, height);
		if(image == 0)
			return;
		NVGPaint paint = scratchPaint;
		NanoVG.nvgImagePattern(vg, x, y, width, height, 0, image, 1F, paint);
		paint.innerColor(getColor(tint));
		paint.outerColor(getColor(tint));
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
		NanoVG.nvgFillPaint(vg, paint);
		NanoVG.nvgFill(vg);
	}

	public void drawImageRotated(int texture, float centerX, float centerY,
		float width, float height, float radius, float degrees, int tint)
	{
		NanoVG.nvgSave(vg);
		NanoVG.nvgTranslate(vg, centerX, centerY);
		NanoVG.nvgRotate(vg, (float)Math.toRadians(degrees));
		NanoVG.nvgTranslate(vg, -centerX, -centerY);
		drawImage(texture, centerX - width / 2, centerY - height / 2, width,
			height, radius, tint);
		NanoVG.nvgRestore(vg);
	}

	public void drawText(String text, float x, float y, int argb, float size,
		HudFont font)
	{
		if(text == null || text.isEmpty() || font == null
			|| !font.isLoaded())
			return;

		NanoVG.nvgFontSize(vg, size);
		NanoVG.nvgFontFace(vg, font.getName());
		NanoVG.nvgTextAlign(vg,
			NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE);
		NanoVG.nvgFillColor(vg, getColor(argb));
		NanoVG.nvgText(vg, x, y + size / 2, text);
	}

	public void drawCenteredText(String text, float x, float y, int argb,
		float size, HudFont font)
	{
		if(text == null || text.isEmpty())
			return;
		float width = getTextWidth(text, size, font);
		drawText(text, x - width / 2, y, argb, size, font);
	}

	public void drawRoundedRect(float x, float y, float width, float height,
		float radius, Color color)
	{
		if(width < 0 || height < 0)
			return;
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
		NanoVG.nvgFillColor(vg, getColor(color));
		NanoVG.nvgFill(vg);
	}

	public void drawRoundedRectVarying(float x, float y, float width,
		float height, float topLeftRadius, float topRightRadius,
		float bottomLeftRadius, float bottomRightRadius, Color color)
	{
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgRoundedRectVarying(vg, x, y, width, height, topLeftRadius,
			topRightRadius, bottomRightRadius, bottomLeftRadius);
		NanoVG.nvgFillColor(vg, getColor(color));
		NanoVG.nvgFill(vg);
	}

	public void drawGradientRect(float x, float y, float width, float height,
		Color color1, Color color2)
	{
		NVGPaint paint = scratchPaint;
		try
		{
			float tick = (System.currentTimeMillis() % 3600) / 570F;
			float max = Math.max(width, height);
			NanoVG.nvgBeginPath(vg);
			NanoVG.nvgRect(vg, x, y, width, height);
			NanoVG.nvgFillPaint(vg, NanoVG.nvgLinearGradient(vg,
				x + width / 2 - (max / 2) * cos(tick),
				y + height / 2 - (max / 2) * sin(tick),
				x + width / 2 + (max / 2) * cos(tick),
				y + height / 2 + (max / 2) * sin(tick), getColor(color1),
				getColor(color2), paint));
			NanoVG.nvgFill(vg);
		}finally
		{
		}
	}

	public void drawGradientRoundedRect(float x, float y, float width,
		float height, float radius, Color color1, Color color2)
	{
		NVGPaint paint = scratchPaint;
		try
		{
			float tick = (System.currentTimeMillis() % 3600) / 570F;
			float max = Math.max(width, height);
			NanoVG.nvgBeginPath(vg);
			NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
			NanoVG.nvgFillPaint(vg, NanoVG.nvgLinearGradient(vg,
				x + width / 2 - (max / 2) * cos(tick),
				y + height / 2 - (max / 2) * sin(tick),
				x + width / 2 + (max / 2) * cos(tick),
				y + height / 2 + (max / 2) * sin(tick), getColor(color1),
				getColor(color2), paint));
			NanoVG.nvgFill(vg);
		}finally
		{
		}
	}

	public void drawOutlineRoundedRect(float x, float y, float width,
		float height, float radius, float strokeWidth, Color color)
	{
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
		NanoVG.nvgStrokeWidth(vg, strokeWidth);
		NanoVG.nvgStrokeColor(vg, getColor(color));
		NanoVG.nvgStroke(vg);
	}

	public void drawGradientOutlineRoundedRect(float x, float y, float width,
		float height, float radius, float strokeWidth, Color color1,
		Color color2)
	{
		NVGPaint paint = scratchPaint;
		try
		{
			float tick = (System.currentTimeMillis() % 3600) / 570F;
			float max = Math.max(width, height);
			NanoVG.nvgBeginPath(vg);
			NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
			NanoVG.nvgStrokeWidth(vg, strokeWidth);
			NanoVG.nvgStrokePaint(vg, NanoVG.nvgLinearGradient(vg,
				x + width / 2 - (max / 2) * cos(tick),
				y + height / 2 - (max / 2) * sin(tick),
				x + width / 2 + (max / 2) * cos(tick),
				y + height / 2 + (max / 2) * sin(tick), getColor(color1),
				getColor(color2), paint));
			NanoVG.nvgStroke(vg);
		}finally
		{
		}
	}

	public void drawShadow(float x, float y, float width, float height,
		float radius)
	{
		drawShadow(x, y, width, height, radius, 7);
	}

	public void drawShadow(float x, float y, float width, float height,
		float radius, int strength)
	{
		int alpha = 1;
		for(float f = strength; f > 0; f--)
		{
			drawOutlineRoundedRect(x - f / 2, y - f / 2, width + f,
				height + f, radius + 2, f,
				new Color(0, 0, 0, Math.min(255, alpha)));
			alpha += 2;
		}
	}

	public void drawGradientShadow(float x, float y, float width, float height,
		float radius, Color color1, Color color2)
	{
		int alpha = 1;
		for(float f = 10; f > 0; f--)
		{
			drawGradientOutlineRoundedRect(x - f / 2, y - f / 2, width + f,
				height + f, radius + 2, f, applyAlpha(color1, alpha),
				applyAlpha(color2, alpha));
			alpha += 3;
		}
	}

	public void drawCircle(float x, float y, float radius, Color color)
	{
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgCircle(vg, x, y, radius);
		NanoVG.nvgFillColor(vg, getColor(color));
		NanoVG.nvgFill(vg);
	}

	public void drawArc(float x, float y, float radius, float startAngle,
		float endAngle, float strokeWidth, Color color)
	{
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgArc(vg, x, y, radius, (float)Math.toRadians(startAngle),
			(float)Math.toRadians(endAngle), NanoVG.NVG_CW);
		NanoVG.nvgStrokeWidth(vg, strokeWidth);
		NanoVG.nvgStrokeColor(vg, getColor(color));
		NanoVG.nvgStroke(vg);
	}

	// ------------------------------------------------------------------- text
	public void drawText(String text, float x, float y, Color color, float size,
		HudFont font)
	{
		if(text == null || text.isEmpty() || font == null
			|| !font.isLoaded())
			return;

		NanoVG.nvgFontSize(vg, size);
		NanoVG.nvgFontFace(vg, font.getName());
		NanoVG.nvgTextAlign(vg,
			NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE);
		NanoVG.nvgFillColor(vg, getColor(color));
		NanoVG.nvgText(vg, x, y + size / 2, text);
	}

	public void drawText(String text, float x, float y, float size,
		HudFont font, Color color)
	{
		drawText(text, x, y, color, size, font);
	}

	public void drawCenteredText(String text, float x, float y, Color color,
		float size, HudFont font)
	{
		if(text == null || text.isEmpty())
			return;
		float width = getTextWidth(text, size, font);
		drawText(text, x - width / 2, y, color, size, font);
	}

	public void drawCenteredText(String text, float x, float y, float size,
		HudFont font, Color color)
	{
		drawCenteredText(text, x, y, color, size, font);
	}

	public float getTextWidth(String text, float size, HudFont font)
	{
		if(text == null || text.isEmpty() || font == null
			|| !font.isLoaded())
			return 0;
		float[] bounds = new float[4];
		NanoVG.nvgFontSize(vg, size);
		NanoVG.nvgFontFace(vg, font.getName());
		NanoVG.nvgTextBounds(vg, 0, 0, text, bounds);
		NanoVG.nvgTextAlign(vg,
			NanoVG.NVG_ALIGN_LEFT | NanoVG.NVG_ALIGN_MIDDLE);
		return bounds[2] - bounds[0];
	}

	public float getTextHeight(String text, float size, HudFont font)
	{
		if(text == null || text.isEmpty() || font == null
			|| !font.isLoaded())
			return 0;
		float[] bounds = new float[4];
		NanoVG.nvgFontSize(vg, size);
		NanoVG.nvgFontFace(vg, font.getName());
		NanoVG.nvgTextBounds(vg, 0, 0, text, bounds);
		return bounds[3] - bounds[1];
	}

	public String getLimitText(String text, float fontSize, HudFont font,
		float width)
	{
		if(text == null || text.isEmpty())
			return "";
		String result = text;
		boolean removed = false;
		while(!result.isEmpty()
			&& getTextWidth(result, fontSize, font) > width)
		{
			result = result.substring(0, result.length() - 1);
			removed = true;
		}
		return result + (removed ? "..." : "");
	}

	// -------------------------------------------------------------- transform
	public void save()
	{
		NanoVG.nvgSave(vg);
	}

	public void restore()
	{
		NanoVG.nvgRestore(vg);
	}

	public void translate(float x, float y)
	{
		NanoVG.nvgTranslate(vg, x, y);
	}

	public void scale(float x, float y, float scale)
	{
		NanoVG.nvgTranslate(vg, x, y);
		NanoVG.nvgScale(vg, scale, scale);
		NanoVG.nvgTranslate(vg, -x, -y);
	}

	public void scale(float x, float y, float width, float height,
		float scale)
	{
		float centerX = (x + (x + width)) / 2;
		float centerY = (y + (y + height)) / 2;
		NanoVG.nvgTranslate(vg, centerX, centerY);
		NanoVG.nvgScale(vg, scale, scale);
		NanoVG.nvgTranslate(vg, -centerX, -centerY);
	}

	public void rotate(float x, float y, float width, float height,
		float angle)
	{
		float centerX = (x + (x + width)) / 2;
		float centerY = (y + (y + height)) / 2;
		NanoVG.nvgTranslate(vg, centerX, centerY);
		NanoVG.nvgRotate(vg, angle);
		NanoVG.nvgTranslate(vg, -centerX, -centerY);
	}

	public void setAlpha(float alpha)
	{
		NanoVG.nvgGlobalAlpha(vg, alpha);
	}

	public void scissor(float x, float y, float width, float height)
	{
		NanoVG.nvgScissor(vg, x, y, width, height);
	}

	// ----------------------------------------------------------------- images
	public int loadImage(String path)
	{
		if(path == null || path.isBlank())
			return 0;
		Integer cached = images.get("res:" + path);
		if(cached != null)
			return cached;

		try(InputStream in = resource(path))
		{
			if(in == null)
				return 0;
			BufferedImage image = ImageIO.read(in);
			return createImage("res:" + path, image, -1);
		}catch(Exception e)
		{
			return 0;
		}
	}

	public int loadImage(File file)
	{
		if(file == null || !file.isFile())
			return 0;
		String key = "file:" + file.getAbsolutePath();
		Long timestamp = imageTimestamps.get(key);
		int current = (int)(file.lastModified() & 0x7FFFFFFF);
		Integer cached = images.get(key);
		if(cached != null && timestamp != null && timestamp == current)
			return cached;

		try
		{
			if(cached != null)
			{
				NanoVG.nvgDeleteImage(vg, cached);
				images.remove(key);
			}
			BufferedImage image = ImageIO.read(file);
			int handle = createImage(key, image, current);
			imageTimestamps.put(key, (long)current);
			return handle;
		}catch(Exception e)
		{
			return 0;
		}
	}

	private int createImage(String key, BufferedImage image,
		int timestamp)
	{
		if(image == null)
			return 0;

		int width = image.getWidth();
		int height = image.getHeight();
		ByteBuffer buffer = MemoryUtil.memAlloc(width * height * 4);
		try
		{
			for(int y = 0; y < height; y++)
				for(int x = 0; x < width; x++)
				{
					int argb = image.getRGB(x, y);
					buffer.put((byte)(argb >> 16));
					buffer.put((byte)(argb >> 8));
					buffer.put((byte)argb);
					buffer.put((byte)(argb >> 24));
				}
			buffer.flip();

			int handle = NanoVG.nvgCreateImageRGBA(vg, width, height,
				NanoVG.NVG_IMAGE_REPEATX | NanoVG.NVG_IMAGE_REPEATY
					| NanoVG.NVG_IMAGE_GENERATE_MIPMAPS,
				buffer);
			images.put(key, handle);
			if(timestamp >= 0)
				imageTimestamps.put(key, (long)timestamp);
			return handle;
		}finally
		{
			MemoryUtil.memFree(buffer);
		}
	}

	public void drawImage(int image, float x, float y, float width,
		float height)
	{
		if(image == 0)
			return;
		NVGPaint paint = scratchPaint;
		NanoVG.nvgImagePattern(vg, x, y, width, height, 0, image, 1F, paint);
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgRect(vg, x, y, width, height);
		NanoVG.nvgFillPaint(vg, paint);
		NanoVG.nvgFill(vg);
	}

	public void drawRoundedImage(int texture, float x, float y, float width,
		float height, float radius, float alpha)
	{
		int image = textureImage(texture, width, height);
		if(image == 0)
			return;
		NVGPaint paint = scratchPaint;
		try
		{
			NanoVG.nvgImagePattern(vg, x, y, width, height, 0, image, alpha,
				paint);
			NanoVG.nvgBeginPath(vg);
			NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
			NanoVG.nvgFillPaint(vg, paint);
			NanoVG.nvgFill(vg);
		}finally
		{
		}
	}

	public void drawRoundedImage(int texture, float x, float y, float width,
		float height, float radius)
	{
		drawRoundedImage(texture, x, y, width, height, radius, 1F);
	}

	/**
	 * Draws a normalised sub-rectangle of a texture, such as a sprite from the
	 * GUI atlas. UVs use the same top-left convention as
	 * {@code TextureAtlasSprite#getU0()}.
	 */
	public void drawImageRegion(int texture, float x, float y, float width,
		float height, float u0, float v0, float u1, float v1)
	{
		if(texture <= 0 || width <= 0 || height <= 0 || u1 <= u0 || v1 <= v0)
			return;

		int image = textureImage(texture, width, height);
		if(image == 0)
			return;

		float scaleX = width / (u1 - u0);
		float scaleY = height / (v1 - v0);
		NVGPaint paint = scratchPaint;
		NanoVG.nvgImagePattern(vg, x - u0 * scaleX, y - v0 * scaleY, scaleX,
			scaleY, 0, image, 1F, paint);
		NanoVG.nvgBeginPath(vg);
		NanoVG.nvgRect(vg, x, y, width, height);
		NanoVG.nvgFillPaint(vg, paint);
		NanoVG.nvgFill(vg);
	}

	public void drawRoundedImage(String path, float x, float y, float width,
		float height, float radius)
	{
		int image = loadImage(path);
		if(image == 0)
			return;
		NVGPaint paint = scratchPaint;
		try
		{
			NanoVG.nvgImagePattern(vg, x, y, width, height, 0, image, 1F,
				paint);
			NanoVG.nvgBeginPath(vg);
			NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
			NanoVG.nvgFillPaint(vg, paint);
			NanoVG.nvgFill(vg);
		}finally
		{
		}
	}

	public void drawRoundedImage(File file, float x, float y, float width,
		float height, float radius)
	{
		int image = loadImage(file);
		if(image == 0)
			return;
		NVGPaint paint = scratchPaint;
		try
		{
			NanoVG.nvgImagePattern(vg, x, y, width, height, 0, image, 1F,
				paint);
			NanoVG.nvgBeginPath(vg);
			NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
			NanoVG.nvgFillPaint(vg, paint);
			NanoVG.nvgFill(vg);
		}finally
		{
		}
	}

	public void drawPlayerHead(String texturePath, float x, float y,
		float width, float height, float radius)
	{
		int texture = HudTextures.id(texturePath);
		if(texture <= 0)
			return;

		int image = textureImage(texture, width, height);
		if(image == 0)
			return;

		NVGPaint paint = scratchPaint;
		try
		{
			float sizeMultiplier = 8;
			// Front face.
			NanoVG.nvgImagePattern(vg,
				x - width / 4 * sizeMultiplier / 2,
				y - height / 4 * sizeMultiplier / 2,
				width * sizeMultiplier, height * sizeMultiplier, 0, image, 1F,
				paint);
			NanoVG.nvgBeginPath(vg);
			NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
			NanoVG.nvgFillPaint(vg, paint);
			NanoVG.nvgFill(vg);

			// Hat/overlay layer.
			NanoVG.nvgImagePattern(vg,
				x - width * 3.25F * sizeMultiplier / 2,
				y - height / 4 * sizeMultiplier / 2,
				width * sizeMultiplier, height * sizeMultiplier, 0, image, 1F,
				paint);
			NanoVG.nvgBeginPath(vg);
			NanoVG.nvgRoundedRect(vg, x, y, width, height, radius);
			NanoVG.nvgFillPaint(vg, paint);
			NanoVG.nvgFill(vg);
		}finally
		{
		}
	}

	private int textureImage(int texture, float width, float height)
	{
		if(texture <= 0 || width <= 0 || height <= 0)
			return 0;
		int w = Math.max(1, Math.round(width));
		int h = Math.max(1, Math.round(height));
		long key = ((long)texture << 32) | ((long)w << 16) | h;
		Integer cached = textureImages.get(key);
		if(cached != null)
			return cached;

		int image = NanoVGGL3.nvglCreateImageFromHandle(vg, texture, w, -h,
			NanoVGGL3.NVG_IMAGE_NODELETE);
		if(image == 0)
			return 0;
		textureImages.put(key, image);
		return image;
	}

	// ----------------------------------------------------------------- colors
	public NVGColor getColor(int argb)
	{
		NVGColor cached = colors.get(argb);
		if(cached != null)
			return cached;

		if(colors.size() >= MAX_COLORS)
		{
			for(NVGColor color : colors.values())
				color.free();
			colors.clear();
		}

		NVGColor color = NVGColor.create();
		NanoVG.nvgRGBA((byte)(argb >> 16), (byte)(argb >> 8), (byte)argb,
			(byte)(argb >> 24), color);
		colors.put(argb, color);
		return color;
	}

	public NVGColor getColor(Color color)
	{
		if(color == null)
			color = Color.RED;
		return getColor(color.getRGB());
	}

	public static Color applyAlpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(),
			Math.max(0, Math.min(255, alpha)));
	}

	private static float cos(float value)
	{
		return (float)Math.cos(value);
	}

	private static float sin(float value)
	{
		return (float)Math.sin(value);
	}

	// -------------------------------------------------------------- lifecycle
	public void destroy()
	{
		if(vg == 0)
			return;

		try
		{
			if(framebuffer != null)
			{
				if(framebuffer.image() != 0)
					NanoVG.nvgDeleteImage(vg, framebuffer.image());
				NanoVGGL3.nvgluDeleteFramebuffer(vg, framebuffer);
				framebuffer = null;
			}

			for(int image : images.values())
				if(image != 0)
					NanoVG.nvgDeleteImage(vg, image);
			for(int image : textureImages.values())
				if(image != 0)
					NanoVG.nvgDeleteImage(vg, image);
			images.clear();
			textureImages.clear();

			for(NVGColor color : colors.values())
				color.free();
			colors.clear();

			NanoVGGL3.nvgDelete(vg);
		}catch(Throwable t)
		{
			// Shutting down; never let a GL error escape.
		}finally
		{
			if(fallbackFontBuffer != null)
			{
				MemoryUtil.memFree(fallbackFontBuffer);
				fallbackFontBuffer = null;
			}
			vg = 0;
			available = false;
		}
	}
}
