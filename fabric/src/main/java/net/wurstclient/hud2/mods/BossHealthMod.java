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

public final class BossHealthMod extends HUDMod
{
	private static final float WIDTH = 180;

	public BossHealthMod()
	{
		super("BossHealth", "Boss Health",
			"Shows the vanilla boss bars.");
		setTranslationKey("boss_health");
		setDefaultPosition(180, 300);
	}

	@Override
	protected void draw()
	{
		List<HudCompat.BossBarInfo> bars = HudCompat.bossBars();
		if(bars.isEmpty())
		{
			drawBackground(120, 20);
			drawText("No boss", 6, 5, 9, HudFonts.REGULAR,
				getFontColor(200));
			setSize(120, 20);
			return;
		}

		float height = 6 + bars.size() * 26;
		drawBackground(WIDTH, height);
		for(int i = 0; i < bars.size(); i++)
		{
			HudCompat.BossBarInfo bar = bars.get(i);
			float y = 5 + i * 26;
			drawText(bar.name(), 6, y, 9, HudFonts.DEMIBOLD);
			drawRoundedRect(6, y + 12, WIDTH - 12, 6, 3,
				new Color(0, 0, 0, 160));
			drawRoundedRect(6, y + 12, (WIDTH - 12) * bar.progress(), 6, 3,
				new Color(bar.color(), true));
		}
		setSize(Math.round(WIDTH), Math.round(height));
	}
}
