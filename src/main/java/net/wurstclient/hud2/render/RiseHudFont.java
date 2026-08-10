package net.wurstclient.hud2.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/** Shared Rise-style HUD font backed by the bundled rounded font. */
public final class RiseHudFont
{
	private static final ResourceLocation FONT_ID =
		new ResourceLocation("wurst", "rise");

	private RiseHudFont()
	{}

	public static Component text(String text)
	{
		return Component.literal(text).withStyle(style -> style.withFont(FONT_ID));
	}

	public static Component text(Component text)
	{
		MutableComponent copy = text.copy();
		copy.setStyle(copy.getStyle().withFont(FONT_ID));
		return copy;
	}

	public static int width(Font font, String text)
	{
		return font.width(text(text));
	}

	public static int width(Font font, Component text)
	{
		return font.width(text(text));
	}

	public static void draw(GuiGraphics graphics, Font font, String text, int x,
		int y, int color, boolean shadow)
	{
		graphics.drawString(font, text(text), x, y, color, shadow);
	}

	public static void draw(GuiGraphics graphics, Font font, Component text,
		int x, int y, int color, boolean shadow)
	{
		graphics.drawString(font, text(text), x, y, color, shadow);
	}

	public static void drawCentered(GuiGraphics graphics, Font font,
		Component text, int centerX, int y, int color)
	{
		Component styled = text(text);
		graphics.drawString(font, styled, centerX - font.width(styled) / 2, y,
			color, false);
	}
}
