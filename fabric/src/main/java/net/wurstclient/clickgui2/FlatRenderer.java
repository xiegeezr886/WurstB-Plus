package net.wurstclient.clickgui2;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.theme.FlatTheme;
import net.wurstclient.gui.MainMenuBackground;
import net.wurstclient.hud2.nanovg.NanoVGUI;
import net.wurstclient.util.RenderUtils;

/**
 * ClickGUI's flat look, now drawn entirely through NanoVG.
 *
 * <p>The signatures keep accepting {@link GuiGraphics} so that callers do not
 * need to change, but the parameter is only used as the pass context for
 * deferred item stacks.
 */
public final class FlatRenderer
{
	private FlatRenderer()
	{
	}

	public static void drawBackdrop(GuiGraphics context, int width, int height,
		FlatTheme theme)
	{
		MainMenuBackground.render(width, height,
			WurstClient.MC.level != null ? MainMenuBackground.INGAME_ALPHA
				: MainMenuBackground.MENU_ALPHA);
	}

	public static void drawWindowPanel(GuiGraphics context, int x1, int y1,
		int x2, int y2, int radius, FlatTheme theme, boolean focused)
	{
		NanoVGUI.fillRounded(x1 - 2, y1 + 2, x2 + 2, y2 + 3,
			Math.min(3, radius), 0x48000000);
		NanoVGUI.fillRounded(x1, y1, x2, y2, Math.min(2, radius),
			theme.windowFill(focused));
		drawRoundedOutline(context, x1, y1, x2, y2, Math.min(2, radius),
			theme.border(focused));
	}

	public static void drawPopup(GuiGraphics context, int x1, int y1, int x2,
		int y2, int radius, FlatTheme theme)
	{
		NanoVGUI.fillRounded(x1 - 2, y1 + 2, x2 + 2, y2 + 3, 2, 0x50000000);
		NanoVGUI.fillRounded(x1, y1, x2, y2, 2, theme.popupFill());
		drawRoundedOutline(context, x1, y1, x2, y2, 2, theme.border(true));
	}

	public static void drawControl(GuiGraphics context, int x1, int y1,
		int x2, int y2, int radius, FlatTheme theme, float hover,
		boolean active)
	{
		NanoVGUI.fillRounded(x1, y1, x2, y2, Math.min(2, radius),
			theme.controlFill(hover, active));
	}

	public static void drawSliderTrack(GuiGraphics context, int x1, int y1,
		int x2, int y2, float percentage, FlatTheme theme, float hover)
	{
		NanoVGUI.fillRounded(x1, y1, x2, y2, 2, theme.railFill());
		float progressX = x1
			+ Math.round((x2 - x1) * Math.max(0, Math.min(1, percentage)));
		if(progressX > x1)
			NanoVGUI.fillRounded(x1, y1, progressX, y2, 2,
				theme.progressFill(hover));
	}

	public static void drawPanel(GuiGraphics context, int x1, int y1, int x2,
		int y2, int radius, int fillColor, int borderColor)
	{
		NanoVGUI.fillRounded(x1 - 2, y1 + 2, x2 + 2, y2 + 3, 2, 0x50000000);
		NanoVGUI.fillRounded(x1, y1, x2, y2, Math.min(2, radius), borderColor);
		NanoVGUI.fillRounded(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 1, fillColor);
	}

	public static void fillRoundedRect(GuiGraphics context, int x1, int y1,
		int x2, int y2, int radius, int color)
	{
		NanoVGUI.fillRounded(x1, y1, x2, y2, radius, color);
	}

	public static void drawRoundedOutline(GuiGraphics context, int x1, int y1,
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
