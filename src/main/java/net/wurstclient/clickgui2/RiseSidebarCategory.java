/*
 * Port of Rise 6.1.30's CategoryComponent for the 1.20.1 GUI API.
 */
package net.wurstclient.clickgui2;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

final class RiseSidebarCategory
{
	static final int HEIGHT = 20;
	private static final float TEXT_SCALE = 0.78F;

	private final String name;
	private final GuiIcon icon;
	private final RiseAnimation selection =
		new RiseAnimation(RiseAnimation.Easing.LINEAR, 200);

	RiseSidebarCategory(String name, GuiIcon icon)
	{
		this.name = name;
		this.icon = icon;
	}

	void render(GuiGraphics graphics, Font font, int x, int y, int width,
		boolean selected, boolean hovered, int accent, boolean riseMode)
	{
		float selectedProgress = selection.run(selected ? 1 : 0);
		if(riseMode)
		{
			if(selectedProgress > 0.01F)
			{
				int textWidth = Math.round(RiseFont.width(font, name) * TEXT_SCALE);
				int pillWidth = Math.min(width, 25 + textWidth);
				int target = darker(accent);
				RiseShadow.draw(graphics, x, y + 2, x + pillWidth, y + 18, 5,
					6, target & 0xFFFFFF
						| Math.round(selectedProgress * 40) << 24);
				FlatUiRenderer.fill(graphics, x, y + 2, x + pillWidth, y + 18,
					5, target & 0xFFFFFF
						| Math.round(selectedProgress * 255) << 24);
			}

			int color = (selected ? 0xFF : 0xC8) << 24 | 0xFFFFFF;
			int slide = Math.round(selectedProgress * 3.1875F);
			icon.draw(graphics, x + 4 + slide, y + 6, 8,
				color);
			drawText(graphics, font, name, x + 17 + slide,
				y + 6, color);
			return;
		}

		// 浅色 PVPUtils 风格。
		if(selectedProgress > 0.01F)
		{
			int pillWidth = Math.min(width, 25
				+ Math.round(RiseFont.width(font, name) * TEXT_SCALE));
			FlatUiRenderer.fill(graphics, x, y, x + pillWidth, y + 20, 8,
				PvPUtilsTheme.ACCENT_PILL);
		}else if(hovered)
			FlatUiRenderer.fill(graphics, x, y, x + 25, y + 20, 8,
				PvPUtilsTheme.HOVER_PILL);
		int color = selected ? PvPUtilsTheme.ACCENT : hovered
			? PvPUtilsTheme.TEXT_ICON : PvPUtilsTheme.TEXT_ROW;
		icon.draw(graphics, x + 4, y + 6, 8, color);
		drawText(graphics, font, name, x + 17, y + 6, color);
	}

	private static int darker(int color)
	{
		int red = Math.round((color >> 16 & 0xFF) * 0.7F);
		int green = Math.round((color >> 8 & 0xFF) * 0.7F);
		int blue = Math.round((color & 0xFF) * 0.7F);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}

	private static void drawText(GuiGraphics graphics, Font font, String text,
		int x, int y, int color)
	{
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1);
		RiseFont.draw(graphics, font, text, 0, 0, color);
		graphics.pose().popPose();
	}
}
