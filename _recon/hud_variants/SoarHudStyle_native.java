package net.wurstclient.hud2;

import net.minecraft.client.gui.Font;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Shared drawing helpers for the Soar-style HUD: a translucent black rounded
 * panel with a soft drop shadow and white text.
 *
 * <p>
 * The values mirror Soar's {@code SimpleHUDMod}: 9px text, 5px padding,
 * radius 6 and a {@code Color(0, 0, 0, 100)} background.
 */
public final class SoarHudStyle
{
	public static final int RADIUS = 6;
	public static final int PADDING = 5;
	public static final int VERTICAL_PADDING = 4;
	public static final int PANEL_COLOR = 0x64000000;
	public static final int SHADOW_COLOR = 0x28000000;
	public static final int TEXT_COLOR = 0xFFFFFFFF;
	public static final int MUTED_TEXT_COLOR = 0xCCFFFFFF;
	public static final int ACCENT_COLOR = 0xFF006366;

	private SoarHudStyle()
	{
	}

	public static void panel(GuiGraphicsExtractor graphics, int x1, int y1,
		int x2, int y2)
	{
		panel(graphics, x1, y1, x2, y2, RADIUS);
	}

	public static void panel(GuiGraphicsExtractor graphics, int x1, int y1,
		int x2, int y2, int radius)
	{
		if(x2 <= x1 || y2 <= y1)
			return;

		FlatRenderer.fillRoundedRect(graphics, x1, y1 + 1, x2, y2 + 2,
			radius, SHADOW_COLOR);
		FlatRenderer.fillRoundedRect(graphics, x1, y1, x2, y2, radius,
			PANEL_COLOR);
	}

	public static int lineHeight()
	{
		return WurstClient.MC.font.lineHeight;
	}

	public static int singleLineHeight()
	{
		return Math.max(panelHeight(lineHeight()), lineHeight()
			+ VERTICAL_PADDING * 2);
	}

	public static int panelHeight(int contentHeight)
	{
		return contentHeight + VERTICAL_PADDING * 2;
	}

	public static int textY(int panelY, int contentHeight)
	{
		return panelY + Math.max(0, (panelHeight(contentHeight)
			- contentHeight) / 2);
	}

	public static int textY(int panelY)
	{
		return textY(panelY, lineHeight());
	}

	public static void text(GuiGraphicsExtractor graphics, Font font,
		String text, int x, int y)
	{
		graphics.text(font, text, x, y, TEXT_COLOR, false);
	}

	public static void text(GuiGraphicsExtractor graphics, Font font,
		String text, int x, int y, int color)
	{
		graphics.text(font, text, x, y, color, false);
	}

	public static int withAlpha(int color, float opacity)
	{
		int alpha = Math.round((color >>> 24) * clamp(opacity));
		return alpha << 24 | color & 0x00FFFFFF;
	}

	private static float clamp(float value)
	{
		return Math.max(0, Math.min(1, value));
	}
}
