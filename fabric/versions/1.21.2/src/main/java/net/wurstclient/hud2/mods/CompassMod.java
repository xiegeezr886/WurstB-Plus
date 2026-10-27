/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.awt.Color;

import net.minecraft.util.Mth;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.nanovg.HudFonts;

public final class CompassMod extends HUDMod
{
	private static final float WIDTH = 160;
	private static final float HEIGHT = 26;

	public CompassMod()
	{
		super("Compass", "Compass", "Shows a horizontal compass strip.");
		setTranslationKey("compass");
		setDefaultPosition(180, 600);
	}

	@Override
	protected void draw()
	{
		drawBackground(WIDTH, HEIGHT);
		if(MC.player == null)
		{
			setSize(Math.round(WIDTH), Math.round(HEIGHT));
			return;
		}

		float yaw = Mth.wrapDegrees(MC.player.getYRot());
		float center = WIDTH / 2;

		for(int angle = -180; angle <= 180; angle += 15)
		{
			float offset = Mth.wrapDegrees(angle - yaw);
			if(Math.abs(offset) > 95)
				continue;

			float x = center + offset * (WIDTH / 180F);
			boolean major = angle % 90 == 0;
			drawRect(x, 15, 1, major ? 7 : 4,
				new Color(major ? 255 : 255, 255, 255,
					major ? 210 : 110));

			if(major)
				drawCenteredText(cardinal(angle), x, 3, 9,
					HudFonts.DEMIBOLD);
		}

		drawRect(center, 13, 1, 11, new Color(accentColor(255), true));
		setSize(Math.round(WIDTH), Math.round(HEIGHT));
	}

	private String cardinal(int angle)
	{
		return switch(Math.floorMod(angle, 360))
		{
			case 0, 180 -> "S";
			case 90 -> "W";
			case 270 -> "E";
			default -> "";
		};
	}
}
