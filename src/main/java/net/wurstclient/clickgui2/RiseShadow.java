package net.wurstclient.clickgui2;

import net.minecraft.client.gui.GuiGraphics;

final class RiseShadow
{
	private RiseShadow()
	{
	}

	static void draw(GuiGraphics graphics, float x1, float y1, float x2,
		float y2, float radius, int blur, int color)
	{
		int sourceAlpha = color >>> 24;
		if(sourceAlpha == 0 || blur <= 0)
			return;
		int steps = Math.max(2, Math.min(9, blur / 2));
		for(int step = steps; step >= 1; step--)
		{
			float spread = blur * step / (float)steps;
			float strength = 1 - (step - 1F) / steps;
			int alpha = Math.max(1,
				Math.round(sourceAlpha * strength * strength / steps));
			FlatUiRenderer.fill(graphics, x1 - spread, y1 - spread,
				x2 + spread, y2 + spread, radius + spread,
				alpha << 24 | color & 0xFFFFFF);
		}
	}
}
