package net.wurstclient.clickgui2;

import net.minecraft.client.gui.GuiGraphics;
import net.wurstclient.hud2.nanovg.NanoVGUI;

final class RoundedRectRenderer
{
	private RoundedRectRenderer()
	{
	}

	public static void fill(GuiGraphics graphics, float x1, float y1, float x2,
		float y2, float radius, int color)
	{
		NanoVGUI.fillRounded(x1, y1, x2, y2, radius, color);
	}

	public static void outline(GuiGraphics graphics, float x1, float y1,
		float x2, float y2, float radius, int color)
	{
		NanoVGUI.outlineRounded(x1, y1, x2, y2, radius, color);
	}
}
