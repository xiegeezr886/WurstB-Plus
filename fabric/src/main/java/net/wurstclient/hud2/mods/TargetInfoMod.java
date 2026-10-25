/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.nanovg.HudFonts;

public final class TargetInfoMod extends HUDMod
{
	public TargetInfoMod()
	{
		super("TargetInfo", "Target Info",
			"Shows information about the entity being looked at.");
		setTranslationKey("target_info");
		setDefaultPosition(180, 54);
	}

	@Override
	protected void draw()
	{
		HudCompat.TargetInfo target = HudCompat.target();
		if(target == null)
		{
			drawBackground(120, 20);
			drawText("No target", 6, 5, 9, HudFonts.REGULAR,
				getFontColor(200));
			setSize(120, 20);
			return;
		}

		float width = 170;
		float height = target.skin() == null ? 34 : 44;
		drawBackground(width, height);

		float textX = 6;
		if(target.skin() != null)
		{
			drawPlayerHead(target.skin(), 6, 6, 30, 30, 6);
			textX = 42;
		}

		drawText(target.name(), textX, 5, 10, HudFonts.DEMIBOLD);
		drawText(String.format("%.1f / %.1f HP", target.health(),
			target.maxHealth()), textX, 19, 9, HudFonts.REGULAR);
		drawText(String.format("%.1f m", target.distance()), textX, 31, 8,
			HudFonts.REGULAR, getFontColor(200));
		setSize(Math.round(width), Math.round(height));
	}
}
