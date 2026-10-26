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

import net.wurstclient.Feature;
import net.wurstclient.hack.Hack;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.nanovg.HudFonts;

public final class ArrayListMod extends HUDMod
{
	public ArrayListMod()
	{
		super("ArrayList", "ArrayList", "Lists all enabled modules.");
		setTranslationKey("array_list");
		setDefaultPosition(700, 10);
	}

	@Override
	protected void draw()
	{
		if(getManager() == null)
			return;

		// The module list covers every enabled feature: hacks as well as HUD
		// modules (previously only HUD modules were listed).
		List<Feature> active = new ArrayList<>();
		if(WURST.getHax() != null)
			for(Hack hack : WURST.getHax().getAllHax())
				if(hack.isEnabled())
					active.add(hack);

		for(HUDMod mod : getManager().getMods().values())
			if(mod != this && mod.isEnabled())
				active.add(mod);

		active.sort(Comparator.comparing(Feature::getDisplayName,
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
		for(Feature feature : active)
			width = Math.max(width,
				getTextWidth(feature.getDisplayName(), 9, HudFonts.REGULAR)
					+ 12);

		float height = 6 + active.size() * 13;
		drawBackground(width, height);
		for(int i = 0; i < active.size(); i++)
			drawText(active.get(i).getDisplayName(), 6, 4 + i * 13, 9,
				HudFonts.REGULAR);
		setSize(Math.round(width), Math.round(height));
	}
}
