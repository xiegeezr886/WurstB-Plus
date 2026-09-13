/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.clickgui2;

import net.wurstclient.util.render.GuiGraphicsExtractor;
import net.wurstclient.WurstClient;
import net.wurstclient.util.RenderUtils;

public enum ClickGuiIcons
{
	;
	
	public static void drawMinimizeArrow(GuiGraphicsExtractor context, float x1,
		float y1, float x2, float y2, boolean hovering, boolean minimized)
	{
		ClickGui gui = WurstClient.INSTANCE.getGui();
		int color = hovering ? gui.getTheme().accent(1)
			: gui.getTheme().text(0.76F);
		float middleX = (x1 + x2) / 2;
		float middleY = (y1 + y2) / 2;
		float tipY = minimized ? middleY + 2 : middleY - 2;
		float sideY = minimized ? middleY - 1 : middleY + 1;
		float[][] chevron = {{x1 + 2, sideY}, {middleX, tipY},
			{x2 - 2, sideY}};
		RenderUtils.drawLineStrip2D(context, chevron, color);
	}

	public static void drawWindowToggle(GuiGraphicsExtractor context, float x1,
		float y1, float x2, float y2, boolean hovering, boolean minimized)
	{
		ClickGui gui = WurstClient.INSTANCE.getGui();
		int color = hovering ? gui.getTheme().accent(1)
			: gui.getTheme().text(0.76F);
		int size = Math.max(1, Math.round(Math.min(x2 - x1, y2 - y1)));
		GuiIcon.WINDOW_TOGGLE.drawRotated(context, Math.round(x1),
			Math.round(y1), size, color, minimized ? 0 : 180);
	}
	
	public static void drawRadarArrow(GuiGraphicsExtractor context, float x1, float y1,
		float x2, float y2)
	{
		float x3 = x1 + (x2 - x1) / 2;
		float y3 = y1 + (y2 - y1) * 0.75F;
		
		// arrow
		ClickGui gui = WurstClient.INSTANCE.getGui();
		int arrowColor = gui.getTheme().accent(1);
		float[][] arrowVertices = {{x3, y1}, {x1, y2}, {x3, y3}, {x2, y2}};
		RenderUtils.fillQuads2D(context, arrowVertices, arrowColor);
		
		// outline
		int outlineColor = gui.getTheme().background(0.9F);
		RenderUtils.drawLineStrip2D(context, arrowVertices, outlineColor);
	}
	
	public static void drawPin(GuiGraphicsExtractor context, float x1, float y1,
		float x2, float y2, boolean hovering, boolean pinned)
	{
		ClickGui gui = WurstClient.INSTANCE.getGui();
		int color = pinned ? gui.getTheme().accent(hovering ? 1 : 0.9F)
			: gui.getTheme().text(hovering ? 0.95F : 0.68F);
		int size = Math.max(1, Math.round(Math.min(x2 - x1, y2 - y1)));
		GuiIcon.PIN.drawRotated(context, Math.round(x1), Math.round(y1), size,
			color, pinned ? 0 : -45);
	}
	
	public static void drawCheck(GuiGraphicsExtractor context, float x1, float y1,
		float x2, float y2, boolean hovering, boolean grayedOut)
	{
		float xc1 = x1 + 2.5F;
		float xc2 = x1 + 3.5F;
		float xc3 = (x1 + x2) / 2 - 1;
		float xc4 = x2 - 3.5F;
		float xc5 = x2 - 2.5F;
		float yc1 = y1 + 2.5F;
		float yc2 = y1 + 3.5F;
		float yc3 = (y1 + y2) / 2;
		float yc4 = yc3 + 1;
		float yc5 = y2 - 4.5F;
		float yc6 = y2 - 2.5F;
		
		// check
		int checkColor =
			grayedOut ? 0xC0808080 : hovering ? 0xFF00FF00 : 0xFF00D900;
		float[][] checkVertices = {{xc2, yc3}, {xc1, yc4}, {xc3, yc6},
			{xc3, yc5}, {xc3, yc5}, {xc3, yc6}, {xc5, yc2}, {xc4, yc1}};
		RenderUtils.fillQuads2D(context, checkVertices, checkColor);
		
		// outline
		int outlineColor = 0x80101010;
		float[][] outlineVertices = {{xc2, yc3}, {xc3, yc5}, {xc4, yc1},
			{xc5, yc2}, {xc3, yc6}, {xc1, yc4}, {xc2, yc3}};
		RenderUtils.drawLineStrip2D(context, outlineVertices, outlineColor);
	}
	
	public static void drawCross(GuiGraphicsExtractor context, float x1, float y1,
		float x2, float y2, boolean hovering)
	{
		ClickGui gui = WurstClient.INSTANCE.getGui();
		int color = hovering ? gui.getTheme().accent(1)
			: gui.getTheme().text(0.72F);
		RenderUtils.drawLine2D(context, x1 + 2, y1 + 2, x2 - 2, y2 - 2,
			color);
		RenderUtils.drawLine2D(context, x2 - 2, y1 + 2, x1 + 2, y2 - 2,
			color);
	}

	public static void drawSettingsClose(GuiGraphicsExtractor context, float x1,
		float y1, float x2, float y2, boolean hovering)
	{
		ClickGui gui = WurstClient.INSTANCE.getGui();
		int color = hovering ? gui.getTheme().accent(1)
			: gui.getTheme().text(0.72F);
		int size = Math.max(1, Math.round(Math.min(x2 - x1, y2 - y1)));
		GuiIcon.CLOSE.draw(context, Math.round(x1), Math.round(y1), size, color);
	}
}
