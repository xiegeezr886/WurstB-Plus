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
import java.util.List;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.settings.CheckboxSetting;

public final class ScoreboardMod extends HUDMod
{
	private final CheckboxSetting showNumbers = new CheckboxSetting(
		"Numbers", "Shows each entry's score.", true);

	public ScoreboardMod()
	{
		super("Scoreboard", "Scoreboard",
			"Shows the sidebar scoreboard.");
		setTranslationKey("scoreboard");
		setDefaultPosition(180, 110);
		addSetting(showNumbers);
	}

	@Override
	protected void draw()
	{
		List<HudCompat.ScoreLine> lines = HudCompat.scoreboard();
		String title = HudCompat.scoreboardTitle();
		if(lines.isEmpty())
		{
			drawBackground(120, 20);
			drawText("No scoreboard", 6, 5, 9, HudFonts.REGULAR,
				getFontColor(200));
			setSize(120, 20);
			return;
		}

		float width = Math.max(120,
			getLegacyTextWidth(title, 10, HudFonts.DEMIBOLD) + 12);
		for(HudCompat.ScoreLine line : lines)
		{
			float lineWidth = getLegacyTextWidth(line.name(), 9,
				HudFonts.REGULAR);
			if(showNumbers.isChecked())
				lineWidth += 6 + getTextWidth(Integer.toString(line.score()), 9,
					HudFonts.REGULAR);
			width = Math.max(width, lineWidth + 12);
		}

		float height = 8 + 14 + lines.size() * 12;
		drawBackground(width, height);
		drawLegacyText(title, 6, 4, 10, HudFonts.DEMIBOLD);
		for(int i = 0; i < lines.size(); i++)
		{
			HudCompat.ScoreLine line = lines.get(i);
			drawLegacyText(line.name(), 6, 20 + i * 12, 9,
				HudFonts.REGULAR);
			if(!showNumbers.isChecked())
				continue;

			String number = Integer.toString(line.score());
			float nameWidth = getLegacyTextWidth(line.name(), 9,
				HudFonts.REGULAR);
			drawText(number, 6 + nameWidth + 6, 20 + i * 12, 9,
				HudFonts.REGULAR, new Color(0xFFFF5555));
		}
		setSize(Math.round(width), Math.round(height));
	}
}
