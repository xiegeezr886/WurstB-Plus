/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.nanovg.HudFonts;

public final class ArrayListMod extends HUDMod
{
	public ArrayListMod()
	{
		super("ArrayList", "ArrayList",
			"Lists all enabled HUD modules.");
		setTranslationKey("array_list");
		setDefaultPosition(700, 10);
	}

	@Override
	protected void draw()
	{
		if(getManager() == null)
			return;

		List<HUDMod> active = new ArrayList<>();
		for(HUDMod mod : getManager().getMods().values())
			if(mod != this && mod.isEnabled())
				active.add(mod);
		active.sort(Comparator.comparing(HUDMod::getTranslatedName,
			String.CASE_INSENSITIVE_ORDER));

		if(active.isEmpty())
		{
			drawBackground(100, 20);
			drawText("No modules", 6, 5, 9, HudFonts.REGULAR,
				getFontColor(200));
			setSize(100, 20);
			return;
		}

		float width = 100;
		for(HUDMod mod : active)
			width = Math.max(width, getTextWidth(mod.getTranslatedName(), 9,
				HudFonts.REGULAR) + 18);

		float height = 6 + active.size() * 13;
		drawBackground(width, height);
		for(int i = 0; i < active.size(); i++)
		{
			drawRoundedRect(5, 5 + i * 13, 2, 9, 1,
				new java.awt.Color(accentColor(255), true));
			drawText(active.get(i).getTranslatedName(), 11, 4 + i * 13, 9,
				HudFonts.REGULAR);
		}
		setSize(Math.round(width), Math.round(height));
	}
}
