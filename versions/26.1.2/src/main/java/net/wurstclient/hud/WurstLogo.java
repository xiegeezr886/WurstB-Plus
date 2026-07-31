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
		WurstLogoOtf otf = WURST.getOtfs().wurstLogoOtf;
		String brand = WurstClient.CLIENT_NAME;
		String version = getVersionString();
		Font font = WurstClient.MC.font;
		int accent;
		if(WURST.getHax().rainbowUiHack.isEnabled())
			accent = RenderUtils.toIntColor(WURST.getGui().getAcColor(), 1);
		else
			accent = otf.getBackgroundColor() | 0xFF000000;

		int right = x + getWidth();
		int bottom = y + PANEL_HEIGHT;
		int textColor = ensureReadable(otf.getTextColor());
		FlatRenderer.fillRoundedRect(context, x, y, right, bottom,
			5, 0x74070A0F);
		FlatRenderer.drawRoundedOutline(context, x, y, right,
			bottom, 5, 0x2CFFFFFF);
		FlatRenderer.fillRoundedRect(context, x + 1, y + 4,
			x + 3, bottom - 4, 1, accent);

		int textY = y + (PANEL_HEIGHT - font.lineHeight) / 2 + 1;
		context.text(font, brand, x + 8, textY, textColor, false);
		context.text(font, version, x + 14 + font.width(brand),
			textY, withAlpha(textColor, 150), false);
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
