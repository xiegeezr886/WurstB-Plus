package net.wurstclient.clickgui2;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.wurstclient.clickgui2.theme.FlatTheme;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.util.RenderUtils;

/**
 * ClickGUI's flat look, now drawn entirely through NanoVG.
 *
 * <p>The signatures keep accepting {@link GuiGraphicsExtractor} so that callers do not
 * need to change, but the parameter is only used as the pass context for
 * deferred item stacks.
 */
public final class FlatRenderer
{
	private FlatRenderer()
	{
	}

	public static void drawBackdrop(GuiGraphicsExtractor context, int width, int height,
		FlatTheme theme)
	{
		NanoVGUI.fill(0, 0, width, height, 0x18000000);
	}

	public static void drawWindowPanel(GuiGraphicsExtractor context, int x1, int y1,
		int x2, int y2, int radius, FlatTheme theme, boolean focused)
	{
		int safeRadius = Math.max(0, radius);
		NanoVGUI.fillRounded(x1 - 2, y1 + 2, x2 + 2, y2 + 3,
			Math.min(3, radius), 0x48000000);
		NanoVGUI.fillRounded(x1, y1, x2, y2, Math.min(2, radius),
			theme.windowFill(focused));
		drawRoundedOutline(context, x1, y1, x2, y2, Math.min(2, radius),
			theme.border(focused));
		fillRoundedRect(context, x1 + 1, y1 + 1, x2 - 1, y2 - 1,
			Math.max(0, safeRadius - 1), theme.windowFill(focused));
	}

	public static void drawPopup(GuiGraphicsExtractor context, int x1, int y1, int x2,
		int y2, int radius, FlatTheme theme)
	{
		NanoVGUI.fillRounded(x1 - 2, y1 + 2, x2 + 2, y2 + 3, 2, 0x50000000);
		NanoVGUI.fillRounded(x1, y1, x2, y2, 2, theme.popupFill());
		drawRoundedOutline(context, x1, y1, x2, y2, 2, theme.border(true));
	}

	public static void drawControl(GuiGraphicsExtractor context, int x1, int y1,
		int x2, int y2, int radius, FlatTheme theme, float hover,
		boolean active)
	{
		NanoVGUI.fillRounded(x1, y1, x2, y2, Math.min(2, radius),
			theme.controlFill(hover, active));
	}

	public static void drawSliderTrack(GuiGraphicsExtractor context, int x1, int y1,
		int x2, int y2, float percentage, FlatTheme theme, float hover)
	{
		NanoVGUI.fillRounded(x1, y1, x2, y2, 2, theme.railFill());
		float progressX = x1
			+ Math.round((x2 - x1) * Math.max(0, Math.min(1, percentage)));
		if(progressX > x1)
			NanoVGUI.fillRounded(x1, y1, progressX, y2, 2,
				theme.progressFill(hover));
	}

	public static void drawPanel(GuiGraphicsExtractor context, int x1, int y1, int x2,
		int y2, int radius, int fillColor, int borderColor)
	{
		NanoVGUI.fillRounded(x1 - 2, y1 + 2, x2 + 2, y2 + 3, 2, 0x50000000);
		NanoVGUI.fillRounded(x1, y1, x2, y2, Math.min(2, radius), borderColor);
		NanoVGUI.fillRounded(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 1, fillColor);
	}

	public static void fillRoundedRect(GuiGraphicsExtractor context, int x1, int y1,
		int x2, int y2, int radius, int color)
	{
		NanoVGUI.fillRounded(x1, y1, x2, y2, radius, color);
	}

	public static void drawRoundedOutline(GuiGraphicsExtractor context, int x1, int y1,
		int x2, int y2, int radius, int color)
	{
		NanoVGUI.outlineRounded(x1, y1, x2, y2, radius, color);
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
