package net.wurstclient.clickgui2;

import net.minecraft.client.gui.GuiGraphicsExtractor;

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
		fill(graphics, x1 - 3, y1 + 2, x2 + 3, y2 + 5, radius + 2,
			0x50000000);
		fill(graphics, x1, y1, x2, y2, radius, fillColor);
		outline(graphics, x1, y1, x2, y2, radius, borderColor);
	}
}
