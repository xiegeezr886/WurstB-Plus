/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.nanovg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.wurstclient.WurstClient;
import net.wurstclient.hud2.compat.HudTextures;
import net.wurstclient.util.RenderUtils;

/**
 * Shared NanoVG drawing front-end for every custom-drawn screen.
 *
 * <p>Each screen wraps its custom drawing in {@link #pass(GuiGraphics, Runnable)}.
 * The pass renders into NanoVG's offscreen framebuffer and composites the
 * result onto Minecraft's main target, so the vanilla pipeline is never left in
 * an unexpected state. Vanilla widgets that must keep using {@link GuiGraphics}
 * (EditBox, Button, item stacks) can be drawn between two passes.
 *
 * <p>Coordinates are Minecraft GUI coordinates (the same values the old
 * {@code GuiGraphics} calls used). Text is rendered with the bundled MiSans
 * fonts and understands legacy {@code §} colour codes.
 */
public final class NanoVGUI
{
	public static final float TEXT_SIZE = 9F;
	public static final float SMALL_TEXT_SIZE = 8F;
	public static final float TITLE_SIZE = 9.5F;

	private static final NanoVGContext NVG = NanoVGContext.get();
	private static final HudFont FONT = HudFonts.REGULAR;
	private static final int[] LEGACY_COLORS = {0x000000, 0x0000AA,
		0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
		0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF,
		0xFFFF55, 0xFFFFFF};

	private static boolean active;
	private static float translateX;
	private static float translateY;

	private static final Deque<float[]> TRANSFORMS = new ArrayDeque<>();
	private static final Deque<float[]> SCISSORS = new ArrayDeque<>();
	private static final List<DeferredItem> ITEMS = new ArrayList<>();
	private static final List<Runnable> DEFERRED = new ArrayList<>();

	private NanoVGUI() {}

	public static boolean isActive()
	{
		return active;
	}

	public static NanoVGContext context()
	{
		return NVG;
	}

	/**
	 * Runs {@code body} inside a NanoVG frame and composites the result.
	 */
	public static void pass(GuiGraphics graphics, Runnable body)
	{
		if(active)
		{
			body.run();
			return;
		}

		Minecraft mc = WurstClient.MC;
		if(mc == null || graphics == null || !NVG.create())
			return;

		graphics.flush();

		try(GlStateGuard ignored = GlStateGuard.capture())
		{
			if(!NVG.beginFrame())
				return;

			active = true;
			translateX = 0;
			translateY = 0;
			TRANSFORMS.clear();
			SCISSORS.clear();
			ITEMS.clear();

			try
			{
				body.run();
			}finally
			{
				active = false;
				NVG.endFrame();
			}

			RenderTarget target = mc.getMainRenderTarget();
			target.bindWrite(false);
			GL11.glViewport(0, 0, target.width, target.height);
			NVG.composite();
		}catch(Throwable t)
		{
			System.err.println("[GUI] NanoVG pass failed");
			t.printStackTrace();
		}finally
		{
			active = false;
			TRANSFORMS.clear();
			SCISSORS.clear();
		}

		flushDeferredItems(graphics);
	}

	/**
	 * Queues NanoVG drawing to be replayed by {@link #runDeferred()} inside a
	 * later pass. Used by vanilla list entries, which render before the
	 * screen's own NanoVG pass.
	 */
	public static void defer(Runnable action)
	{
		DEFERRED.add(action);
	}

	public static void clearDeferred()
	{
		DEFERRED.clear();
	}

	public static void runDeferred()
	{
		if(DEFERRED.isEmpty())
			return;
		List<Runnable> actions = new ArrayList<>(DEFERRED);
		DEFERRED.clear();
		for(Runnable action : actions)
			action.run();
	}

	public static void flushDeferredItems(GuiGraphics graphics)
	{
		if(ITEMS.isEmpty())
			return;

		List<DeferredItem> items = new ArrayList<>(ITEMS);
		ITEMS.clear();
		for(DeferredItem item : items)
		{
			graphics.pose().pushPose();
			graphics.pose().last().pose().set(item.pose());
			if(item.scissor() != null)
			{
				float[] s = item.scissor();
				graphics.enableScissor(Math.round(s[0]), Math.round(s[1]),
					Math.round(s[2]), Math.round(s[3]));
			}
			RenderUtils.drawItem(graphics, item.stack(), item.x(), item.y(),
				item.large());
			if(item.scissor() != null)
				graphics.disableScissor();
			graphics.pose().popPose();
		}
	}

	// ------------------------------------------------------------- transforms
	public static void push()
	{
		if(!active)
			return;
		TRANSFORMS.push(new float[]{translateX, translateY});
		NVG.save();
	}

	public static void pop()
	{
		if(!active)
			return;
		NVG.restore();
		float[] state = TRANSFORMS.isEmpty() ? new float[]{0, 0}
			: TRANSFORMS.pop();
		translateX = state[0];
		translateY = state[1];
	}

	public static void translate(float x, float y)
	{
		if(!active)
			return;
		NVG.translate(x, y);
		translateX += x;
		translateY += y;
	}

	public static void rotate(float degrees)
	{
		if(active)
			NVG.rotateRadians((float)Math.toRadians(degrees));
	}

	public static void pushScissor(float x, float y, float width,
		float height)
	{
		if(!active)
			return;
		float[] parent = SCISSORS.peek();
		float left = translateX + x;
		float top = translateY + y;
		float right = left + width;
		float bottom = top + height;
		if(parent != null)
		{
			left = Math.max(left, parent[0]);
			top = Math.max(top, parent[1]);
			right = Math.min(right, parent[2]);
			bottom = Math.min(bottom, parent[3]);
		}
		if(right <= left || bottom <= top)
		{
			SCISSORS.push(new float[]{left, top, left, top});
			NVG.save();
			NVG.scissor(0, 0, 0, 0);
			return;
		}

		SCISSORS.push(new float[]{left, top, right, bottom});
		NVG.save();
		NVG.scissor(x, y, width, height);
	}

	public static void popScissor()
	{
		if(!active)
			return;
		NVG.restore();
		if(!SCISSORS.isEmpty())
			SCISSORS.pop();
	}

	// -------------------------------------------------------------- primitives
	public static void fill(float x1, float y1, float x2, float y2,
		int color)
	{
		if(!active || x2 <= x1 || y2 <= y1)
			return;
		NVG.drawRect(x1, y1, x2 - x1, y2 - y1, color);
	}

	public static void fillGradient(float x1, float y1, float x2, float y2,
		int topColor, int bottomColor)
	{
		if(!active || x2 <= x1 || y2 <= y1)
			return;
		NVG.drawVerticalGradient(x1, y1, x2 - x1, y2 - y1, topColor,
			bottomColor);
	}

	public static void fillHorizontalGradient(float x1, float y1, float x2,
		float y2, int leftColor, int rightColor)
	{
		if(!active || x2 <= x1 || y2 <= y1)
			return;
		NVG.drawHorizontalGradient(x1, y1, x2 - x1, y2 - y1, leftColor,
			rightColor);
	}

	public static void fillRounded(float x1, float y1, float x2, float y2,
		float radius, int color)
	{
		if(!active || x2 <= x1 || y2 <= y1)
			return;
		NVG.drawRoundedRect(x1, y1, x2 - x1, y2 - y1, radius, color);
	}

	public static void outlineRounded(float x1, float y1, float x2, float y2,
		float radius, int color)
	{
		outlineRounded(x1, y1, x2, y2, radius, 1F, color);
	}

	public static void outlineRounded(float x1, float y1, float x2, float y2,
		float radius, float width, int color)
	{
		if(!active || x2 <= x1 || y2 <= y1)
			return;
		NVG.drawOutlineRoundedRect(x1, y1, x2 - x1, y2 - y1, radius, width,
			color);
	}

	public static void circle(float centerX, float centerY, float radius,
		int color)
	{
		if(active)
			NVG.drawCircle(centerX, centerY, radius, color);
	}

	public static void line(float x1, float y1, float x2, float y2, int color)
	{
		line(x1, y1, x2, y2, 1F, color);
	}

	public static void line(float x1, float y1, float x2, float y2,
		float width, int color)
	{
		if(active)
			NVG.drawLine(x1, y1, x2, y2, width, color);
	}

	public static void border(float x1, float y1, float x2, float y2,
		int color)
	{
		if(!active)
			return;
		NVG.drawLine(x1, y1, x2, y1, 1F, color);
		NVG.drawLine(x2, y1, x2, y2, 1F, color);
		NVG.drawLine(x2, y2, x1, y2, 1F, color);
		NVG.drawLine(x1, y2, x1, y1, 1F, color);
	}

	public static void polygon(float[][] points, int color)
	{
		if(active)
			NVG.fillPolygon(points, points.length, color);
	}

	public static void polyline(float[][] points, int color)
	{
		if(active)
			NVG.drawPolyline(points, points.length, 1F, color);
	}

	public static void closedPolyline(float[][] points, int color)
	{
		if(active)
			NVG.drawClosedPolyline(points, points.length, 1F, color);
	}

	public static void image(int texture, float x, float y, float width,
		float height, float radius, int tint)
	{
		if(!active || texture <= 0 || width <= 0 || height <= 0)
			return;
		NVG.drawImage(texture, x, y, width, height, radius, tint);
	}

	public static void image(ResourceLocation location, float x, float y,
		float width, float height, float radius, int tint)
	{
		if(location == null)
			return;
		image(HudTextures.id(location.toString()), x, y, width, height, radius,
			tint);
	}

	public static void imageRotated(int texture, float centerX, float centerY,
		float width, float height, float radius, float degrees, int tint)
	{
		if(!active || texture <= 0)
			return;
		NVG.drawImageRotated(texture, centerX, centerY, width, height, radius,
			degrees, tint);
	}

	public static void imageRotated(ResourceLocation location, float centerX,
		float centerY, float width, float height, float radius, float degrees,
		int tint)
	{
		if(location == null)
			return;
		imageRotated(HudTextures.id(location.toString()), centerX, centerY,
			width, height, radius, degrees, tint);
	}

	// ------------------------------------------------------------------- text
	public static void text(String text, float x, float y, int color)
	{
		text(text, x, y, color, TEXT_SIZE);
	}

	public static void text(String text, float x, float y, int color,
		float size)
	{
		if(!active || text == null || text.isEmpty())
			return;

		int baseAlpha = color >>> 24;
		int current = color;
		StringBuilder segment = new StringBuilder();
		float cursor = x;

		for(int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if(c == '\u00a7' && i + 1 < text.length())
			{
				if(segment.length() > 0)
				{
					NVG.drawText(segment.toString(), cursor, y, current, size,
						FONT);
					cursor += NVG.getTextWidth(segment.toString(), size, FONT);
					segment.setLength(0);
				}

				char code = Character.toLowerCase(text.charAt(++i));
				int parsed = parseColorCode(code);
				if(parsed >= 0)
					current = baseAlpha << 24 | parsed;
				else if(code == 'r')
					current = color;
				continue;
			}
			segment.append(c);
		}

		if(segment.length() > 0)
			NVG.drawText(segment.toString(), cursor, y, current, size, FONT);
	}

	public static void centeredText(String text, float centerX, float y,
		int color)
	{
		centeredText(text, centerX, y, color, TEXT_SIZE);
	}

	public static void centeredText(String text, float centerX, float y,
		int color, float size)
	{
		if(!active || text == null || text.isEmpty())
			return;
		text(text, centerX - width(text, size) / 2, y, color, size);
	}

	public static void rightText(String text, float rightX, float y,
		int color, float size)
	{
		if(!active || text == null || text.isEmpty())
			return;
		text(text, rightX - width(text, size), y, color, size);
	}

	public static float width(String text, float size)
	{
		if(text == null || text.isEmpty())
			return 0;
		return NVG.getTextWidth(stripCodes(text), size, FONT);
	}

	public static float height(float size)
	{
		return Math.max(1, NVG.getTextHeight("Ag", size, FONT));
	}

	public static String plainSubstrByWidth(String text, float maxWidth,
		float size)
	{
		if(text == null || text.isEmpty() || maxWidth <= 0)
			return "";

		StringBuilder out = new StringBuilder();
		float total = 0;
		for(int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if(c == '\u00a7' && i + 1 < text.length())
			{
				out.append(c).append(text.charAt(++i));
				continue;
			}
			float charWidth = NVG.getTextWidth(String.valueOf(c), size, FONT);
			if(total + charWidth > maxWidth)
				break;
			out.append(c);
			total += charWidth;
		}
		return out.toString();
	}

	public static String substrByWidth(String text, float maxWidth, float size)
	{
		if(text == null || text.isEmpty())
			return "";
		if(width(text, size) <= maxWidth)
			return text;

		String ellipsis = "...";
		float ellipsisWidth = width(ellipsis, size);
		String result = plainSubstrByWidth(text, Math.max(0,
			maxWidth - ellipsisWidth), size);
		return result + ellipsis;
	}

	public static String stripCodes(String text)
	{
		if(text == null || text.indexOf('\u00a7') < 0)
			return text;

		StringBuilder out = new StringBuilder(text.length());
		for(int i = 0; i < text.length(); i++)
		{
			char c = text.charAt(i);
			if(c == '\u00a7' && i + 1 < text.length())
			{
				i++;
				continue;
			}
			out.append(c);
		}
		return out.toString();
	}

	private static int parseColorCode(char code)
	{
		if(code >= '0' && code <= '9')
			return LEGACY_COLORS[code - '0'];
		if(code >= 'a' && code <= 'f')
			return LEGACY_COLORS[10 + code - 'a'];
		return -1;
	}

	// ------------------------------------------------------------------ items
	/**
	 * Schedules an item stack to be drawn with the vanilla item renderer right
	 * after the current NanoVG pass. Item models cannot be expressed as NanoVG
	 * primitives, so they are kept on the vanilla pipeline.
	 */
	public static void item(GuiGraphics graphics, ItemStack stack, int x, int y,
		boolean large)
	{
		if(!active)
		{
			RenderUtils.drawItem(graphics, stack, x, y, large);
			return;
		}

		float[] scissor = null;
		if(!SCISSORS.isEmpty())
			scissor = SCISSORS.peek();

		Matrix4f pose = new Matrix4f(graphics.pose().last().pose());
		Matrix4f combined =
			new Matrix4f().translation(translateX, translateY, 0).mul(pose);
		ITEMS.add(new DeferredItem(stack, x, y, large, combined, scissor));
	}

	private record DeferredItem(ItemStack stack, int x, int y, boolean large,
		Matrix4f pose, float[] scissor)
	{
	}
}
