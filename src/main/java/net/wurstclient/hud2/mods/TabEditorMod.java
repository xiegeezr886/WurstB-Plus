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

public final class TabEditorMod extends HUDMod
{
	public TabEditorMod()
	{
		super("TabEditor", "Tab Editor",
			"Shows an extended tab list while the tab key is held.");
		setTranslationKey("tab_editor");
		setDefaultPosition(350, 920);
	}

	@Override
	protected void draw()
	{
		if(!HudCompat.playerListVisible() || MC.player == null)
		{
			if(isEditing())
			{
				drawBackground(190, 32);
				drawText("Tab list", 6, 5, 9, HudFonts.REGULAR);
				drawText("Hold Tab to preview", 6, 17, 8,
					HudFonts.REGULAR, getFontColor(190));
				setSize(190, 32);
			}else
				setSize(0, 0);
			return;
		}

		List<HudCompat.PlayerEntry> players = HudCompat.tabPlayers();
		int rows = Math.min(players.size(), 20);
		float width = 190;
		for(int i = 0; i < rows; i++)
			width = Math.max(width, getTextWidth(line(players.get(i)), 9,
				HudFonts.REGULAR) + 12);
		float height = 8 + rows * 12;
		drawBackground(width, height);
		for(int i = 0; i < rows; i++)
			drawText(line(players.get(i)), 6, 4 + i * 12, 9,
				HudFonts.REGULAR);
		setSize(Math.round(width), Math.round(height));
	}

	private String line(HudCompat.PlayerEntry player)
	{
		return player.name() + "  " + player.ping() + "ms";
	}
}
