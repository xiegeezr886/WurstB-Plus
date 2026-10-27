/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.util.List;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.nanovg.HudFonts;

public final class TNTTimerMod extends HUDMod
{
	public TNTTimerMod()
	{
		super("TNTTimer", "TNT Timer", "Shows the fuse of nearby TNT.");
		setTranslationKey("tnt_timer");
		setDefaultPosition(10, 520);
	}

	@Override
	protected void draw()
	{
		List<HudCompat.TntInfo> tnts = HudCompat.tnts();
		if(tnts.isEmpty())
		{
			drawBackground(90, 20);
			drawText("No TNT", 6, 5, 9, HudFonts.REGULAR,
				getFontColor(200));
			setSize(90, 20);
			return;
		}

		float width = 120;
		int rows = Math.min(tnts.size(), 8);
		for(int i = 0; i < rows; i++)
			width = Math.max(width,
				getTextWidth(line(tnts.get(i)), 9, HudFonts.REGULAR) + 12);

		float height = 6 + rows * 13;
		drawBackground(width, height);
		for(int i = 0; i < rows; i++)
			drawText(line(tnts.get(i)), 6, 4 + i * 13, 9,
				HudFonts.REGULAR);
		setSize(Math.round(width), Math.round(height));
	}

	private String line(HudCompat.TntInfo tnt)
	{
		double distance = MC.player == null ? 0
			: MC.player.distanceToSqr(tnt.x(), tnt.y(), tnt.z());
		return String.format("%.1fs  %.0fm", tnt.fuse() / 20F,
			Math.sqrt(distance));
	}
}
