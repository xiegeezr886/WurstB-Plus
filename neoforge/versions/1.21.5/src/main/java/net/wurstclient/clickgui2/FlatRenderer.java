package net.wurstclient.clickgui2;

import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.wurstclient.clickgui2.theme.FlatTheme;
import net.wurstclient.util.RenderUtils;

public final class FlatRenderer
{
	private FlatRenderer()
	{
	}

	public static void drawBackdrop(GuiGraphicsExtractor context, int width, int height,
		FlatTheme theme)
	{
		context.fill(0, 0, width, height, 0x18000000);
	}

	public static void drawWindowPanel(GuiGraphicsExtractor context, int x1, int y1,
		int x2, int y2, int radius, FlatTheme theme, boolean focused)
	{
		int safeRadius = Math.max(0, radius);
		fillRoundedRect(context, x1, y1, x2, y2, safeRadius,
			theme.border(focused));
		fillRoundedRect(context, x1 + 1, y1 + 1, x2 - 1, y2 - 1,
			Math.max(0, safeRadius - 1), theme.windowFill(focused));
	}

	public static void drawPopup(GuiGraphicsExtractor context, int x1, int y1, int x2,
		int y2, int radius, FlatTheme theme)
	{
		int safeRadius = Math.max(0, radius);
		fillRoundedRect(context, x1, y1, x2, y2, safeRadius,
			theme.border(true));
		fillRoundedRect(context, x1 + 1, y1 + 1, x2 - 1, y2 - 1,
			Math.max(0, safeRadius - 1),
			theme.popupFill());
	}

	public static void drawControl(GuiGraphicsExtractor context, int x1, int y1,
		int x2, int y2, int radius, FlatTheme theme, float hover,
		boolean active)
	{
		fillRoundedRect(context, x1, y1, x2, y2, Math.max(0, radius),
			theme.controlFill(hover, active));
	}

	public static void drawSliderTrack(GuiGraphicsExtractor context, int x1, int y1,
		int x2, int y2, float percentage, FlatTheme theme, float hover)
	{
		fillRoundedRect(context, x1, y1, x2, y2, 2, theme.railFill());
		int progressX = x1 + Math.round((x2 - x1) * Math.max(0,
			Math.min(1, percentage)));
		if(progressX > x1)
			fillRoundedRect(context, x1, y1, progressX, y2, 2,
				theme.progressFill(hover));
	}

	public static void drawPanel(GuiGraphicsExtractor context, int x1, int y1, int x2,
		int y2, int radius, int fillColor, int borderColor)
	{
		int safeRadius = Math.max(0, radius);
		fillRoundedRect(context, x1, y1, x2, y2, safeRadius,
			borderColor);
		fillRoundedRect(context, x1 + 1, y1 + 1, x2 - 1, y2 - 1,
			Math.max(0, safeRadius - 1), fillColor);
	}

	public static void fillRoundedRect(GuiGraphicsExtractor context, int x1, int y1,
		int x2, int y2, int radius, int color)
	{
		RoundedRectRenderer.fill(context, x1, y1, x2, y2, radius, color);
	}

	public static void drawRoundedOutline(GuiGraphicsExtractor context, int x1, int y1,
		int x2, int y2, int radius, int color)
	{
		RoundedRectRenderer.outline(context, x1, y1, x2, y2, radius, color);
	}

	public static int mixColor(float[] base, float[] accent, float weight,
		float opacity)
	{
		float inverseWeight = 1 - weight;
		float[] mixed = {base[0] * inverseWeight + accent[0] * weight,
			base[1] * inverseWeight + accent[1] * weight,
			base[2] * inverseWeight + accent[2] * weight};
		return RenderUtils.toIntColor(mixed, opacity);
	}
}
