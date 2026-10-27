/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.nanovg.HudFonts;

public final class CalendarMod extends HUDMod
{
	private static final DateTimeFormatter DATE =
		DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter TIME =
		DateTimeFormatter.ofPattern("HH:mm");

	public CalendarMod()
	{
		super("Calendar", "Calendar", "Shows the real date and the game day.");
		setTranslationKey("calendar");
		setDefaultPosition(180, 540);
	}

	@Override
	protected void draw()
	{
		String[] lines = {LocalDate.now().format(DATE) + "  "
			+ LocalTime.now().format(TIME),
			"Day " + HudCompat.dayCount()};
		float width = 120;
		for(String line : lines)
			width = Math.max(width,
				getTextWidth(line, 9, HudFonts.REGULAR) + 12);
		float height = 6 + lines.length * 13;
		drawBackground(width, height);
		for(int i = 0; i < lines.length; i++)
			drawText(lines[i], 6, 4 + i * 13, 9, HudFonts.REGULAR);
		setSize(Math.round(width), Math.round(height));
	}
}
