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
import net.wurstclient.settings.CheckboxSetting;

public final class PotionStatusMod extends HUDMod
{
	private final CheckboxSetting showDuration = new CheckboxSetting(
		"Duration", "Shows the remaining effect duration.", true);

	public PotionStatusMod()
	{
		super("PotionStatus", "Potion Status",
			"Lists the player's active potion effects.");
		setTranslationKey("potion_status");
		setDefaultPosition(10, 410);
		addSetting(showDuration);
	}

	@Override
	protected void draw()
	{
		List<HudCompat.EffectInfo> effects = HudCompat.effects();
		if(effects.isEmpty())
		{
			drawBackground(110, 20);
			drawText("No effects", 6, 5, 9, HudFonts.REGULAR,
				getFontColor(200));
			setSize(110, 20);
			return;
		}

		float width = 100;
		for(HudCompat.EffectInfo effect : effects)
			width = Math.max(width,
				getTextWidth(line(effect), 9, HudFonts.REGULAR) + 12);

		float height = 6 + effects.size() * 13;
		drawBackground(width, height);
		for(int i = 0; i < effects.size(); i++)
			drawText(line(effects.get(i)), 6, 4 + i * 13, 9,
				HudFonts.REGULAR);
		setSize(Math.round(width), Math.round(height));
	}

	private String line(HudCompat.EffectInfo effect)
	{
		String text = effect.name();
		if(effect.amplifier() > 0)
			text += " " + (effect.amplifier() + 1);
		if(showDuration.isChecked())
			text += " " + format(effect.durationTicks());
		return text;
	}

	private String format(int ticks)
	{
		int seconds = Math.max(0, ticks / 20);
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
