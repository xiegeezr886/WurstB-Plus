package net.wurstclient.clickgui2;

import net.wurstclient.util.render.GuiGraphicsExtractor;

public final class FlatUiRenderer
{
	private FlatUiRenderer()
	{
	}

	public static void fill(GuiGraphicsExtractor graphics, int x1, int y1, int x2,
		int y2, int radius, int color)
	{
		RoundedRectRenderer.fill(graphics, x1, y1, x2, y2, radius, color);
	}

	public static void outline(GuiGraphicsExtractor graphics, int x1, int y1, int x2,
		int y2, int radius, int color)
	{
		RoundedRectRenderer.outline(graphics, x1, y1, x2, y2, radius, color);
	}

	public static void panel(GuiGraphicsExtractor graphics, int x1, int y1, int x2,
		int y2, int radius, int fillColor, int borderColor)
	{
		fill(graphics, x1, y1, x2, y2, radius, borderColor);
		fill(graphics, x1 + 1, y1 + 1, x2 - 1, y2 - 1,
			Math.max(0, radius - 1), fillColor);
	}
}
