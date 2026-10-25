/*

 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.wurstclient.WurstClient;
import net.wurstclient.clickgui2.FlatRenderer;
import net.wurstclient.other_features.WurstLogoOtf;
import net.wurstclient.util.RenderUtils;

public final class WurstLogo
{
	private static final WurstClient WURST = WurstClient.INSTANCE;
	private static final int PANEL_HEIGHT = 18;
	
	public void render(GuiGraphicsExtractor context)
	{
		if(!WURST.getOtfs().wurstLogoOtf.isVisible())
			return;
		renderAt(context, 4, 4);
	}

	public void renderAt(GuiGraphicsExtractor context, int x, int y)
	{
		String brand = WurstClient.CLIENT_NAME;
		String version = getVersionString();
		Font font = WurstClient.MC.font;
		int textY = y + (PANEL_HEIGHT - font.lineHeight) / 2;
		context.text(font, brand, x + 5, textY, 0xFFFFFFFF, false);
		context.text(font, version, x + 11 + font.width(brand), textY,
			0xCCFFFFFF, false);
	}

	public int getWidth()
	{
		Font font = WurstClient.MC.font;
		return font.width(WurstClient.CLIENT_NAME)
			+ font.width(getVersionString()) + 25;
	}

	public int getHeight()
	{
		return PANEL_HEIGHT;
	}
	
	private String getVersionString()
	{
		String version = "v" + WurstClient.VERSION;
		version += "  MC" + WurstClient.MC_VERSION;
		
		if(WURST.getUpdater().isOutdated())
			version += " (outdated)";
		
		return version;
	}

	private int withAlpha(int color, int alpha)
	{
		return alpha << 24 | color & 0xFFFFFF;
	}

	private int ensureReadable(int color)
	{
		int red = color >> 16 & 0xFF;
		int green = color >> 8 & 0xFF;
		int blue = color & 0xFF;
		float luminance = red * 0.2126F + green * 0.7152F + blue * 0.0722F;
		if(luminance >= 150)
			return color | 0xFF000000;

		float blend = (150 - luminance) / Math.max(1, 255 - luminance);
		red += Math.round((255 - red) * blend);
		green += Math.round((255 - green) * blend);
		blue += Math.round((255 - blue) * blend);
		return 0xFF000000 | red << 16 | green << 8 | blue;
	}
}
