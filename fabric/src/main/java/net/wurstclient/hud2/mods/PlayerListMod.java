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

public final class PlayerListMod extends HUDMod
{
	private final CheckboxSetting always = new CheckboxSetting("Always",
		"Shows the list even when the tab key is not held.", false);

	public PlayerListMod()
	{
		super("PlayerList", "Player List",
			"Shows the players on the server.");
		setTranslationKey("player_list");
		setDefaultPosition(180, 220);
		addSetting(always);
	}

	@Override
	protected void draw()
	{
		List<HudCompat.PlayerEntry> players = HudCompat.tabPlayers();
		boolean visible = always.isChecked()
			|| HudCompat.playerListVisible();
		if(!visible || players.isEmpty())
		{
			drawBackground(120, 20);
			drawText("Tab list hidden", 6, 5, 9, HudFonts.REGULAR,
				getFontColor(200));
			setSize(120, 20);
			return;
		}

		int rows = Math.min(players.size(), 20);
		float width = 130;
		for(int i = 0; i < rows; i++)
			width = Math.max(width, getTextWidth(text(players.get(i)), 9,
				HudFonts.REGULAR) + 12);

		float height = 8 + rows * 12;
		drawBackground(width, height);
		for(int i = 0; i < rows; i++)
			drawText(text(players.get(i)), 6, 4 + i * 12, 9,
				HudFonts.REGULAR);
		setSize(Math.round(width), Math.round(height));
	}

	private String text(HudCompat.PlayerEntry player)
	{
		return player.name() + "  " + player.ping() + "ms";
	}
}
