/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
package net.wurstclient.hud2.mods;

import java.awt.Color;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.nanovg.HudFonts;

public final class LogoMod extends HUDMod
{
	public LogoMod()
	{
		super("Logo", "Logo", "Shows the client name and version.");
		setTranslationKey("logo");
		setDefaultPosition(10, 340);
		setDefaultEnabled(true);
	}

	@Override
	protected void draw()
	{
		String brand = WURST.CLIENT_NAME;
		String version = "v" + WURST.VERSION;

		// Keep the word "Plus" in the theme's accent colour.
		int plusIndex = brand.lastIndexOf(" Plus");
		String brandMain = plusIndex >= 0 ? brand.substring(0, plusIndex)
			: brand;
		String brandAccent = plusIndex >= 0 ? brand.substring(plusIndex) : "";

		float mainWidth = getTextWidth(brandMain, 9, HudFonts.DEMIBOLD);
		float accentWidth = getTextWidth(brandAccent, 9, HudFonts.DEMIBOLD);
		float width = mainWidth + accentWidth
			+ getTextWidth(version, 9, HudFonts.REGULAR) + 18;

		drawBackground(width, 20);
		drawText(brandMain, 6, 5, 9, HudFonts.DEMIBOLD);
		if(!brandAccent.isEmpty())
			drawText(brandAccent, 6 + mainWidth, 5, 9, HudFonts.DEMIBOLD,
				new Color(accentColor(255), true));
		drawText(version, 6 + mainWidth + accentWidth + 4, 5, 9,
			HudFonts.REGULAR, getFontColor(200));
		setSize(Math.round(width), 20);
	}
}
