/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v8.0 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.awt.Color;
import java.util.List;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.HypixelStatsManager;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.compat.HudCompat.PlayerEntry;
import net.wurstclient.hud2.nanovg.HudFonts;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.settings.TextFieldSetting;

/**
 * Bedwars player stats table.
 *
 * <p>Soar v8 gets the values from its websocket backend. This standalone port
 * uses the public Hypixel API with an optional API key; without a key the
 * table still lists tab players and shows dashes.
 */
public final class BedwarsStatsOverlayMod extends HUDMod
{
	private static final float WIDTH = 294;
	private static final int ROW_HEIGHT = 15;

	private final SliderSetting maxPlayers = new SliderSetting("Max players",
		"Maximum number of tab-list players shown.", 16, 1, 30, 1,
		ValueDisplay.INTEGER);

	private final TextFieldSetting apiKey = new TextFieldSetting("API key",
		"Hypixel API key used to fetch Bedwars stats. Leave empty to show"
			+ " player names only.",
		"", value -> true);

	public BedwarsStatsOverlayMod()
	{
		super("BedwarsStats", "Bedwars Stats",
			"Shows Bedwars stats for players in the tab list.");
		setTranslationKey("bedwars_stats");
		setDefaultPosition(300, 120);
		addSetting(maxPlayers);
		addSetting(apiKey);
	}

	@Override
	protected void draw()
	{
		if(!HudCompat.isHypixel())
		{
			drawBackground(226, 20);
			drawText("Bedwars Stats (Hypixel only)", 6, 5, 9,
				HudFonts.REGULAR, getFontColor(200));
			setSize(226, 20);
			return;
		}

		HypixelStatsManager.setApiKey(apiKey.getValue());
		List<PlayerEntry> players = HudCompat.tabPlayers();
		int count = Math.min(maxPlayers.getValueI(), players.size());
		float height = 34 + count * ROW_HEIGHT;

		drawBackground(WIDTH, height);
		drawText("Bedwars Stats", 6, 4, 9, HudFonts.DEMIBOLD);
		drawRect(6, 17, WIDTH - 12, 1, getFontColor(70));

		drawCenteredText("Name", 45, 21, 8, HudFonts.MEDIUM,
			getFontColor(200));
		drawCenteredText("Level", 120, 21, 8, HudFonts.MEDIUM,
			getFontColor(200));
		drawCenteredText("WLR", 170, 21, 8, HudFonts.MEDIUM,
			getFontColor(200));
		drawCenteredText("FKDR", 220, 21, 8, HudFonts.MEDIUM,
			getFontColor(200));
		drawCenteredText("BBLR", 270, 21, 8, HudFonts.MEDIUM,
			getFontColor(200));

		Color rowColor = getFontColor(235);
		Color muted = getFontColor(200);
		for(int i = 0; i < count; i++)
		{
			PlayerEntry player = players.get(i);
			float y = 30 + i * ROW_HEIGHT;
			HypixelStatsManager.Stats stats =
				HypixelStatsManager.get(player.uuid());

			drawPlayerHead(player.skin(), 6, y, 12, 12, 3);
			String name = player.name().length() > 12
				? player.name().substring(0, 12) : player.name();
			drawText(name, 20, y + 2, 8, HudFonts.REGULAR, rowColor);
			drawCenteredText(stats.level(), 120, y + 2, 8,
				HudFonts.REGULAR, muted);
			drawCenteredText(stats.wlr(), 170, y + 2, 8, HudFonts.REGULAR,
				muted);
			drawCenteredText(stats.fkdr(), 220, y + 2, 8, HudFonts.REGULAR,
				muted);
			drawCenteredText(stats.bblr(), 270, y + 2, 8, HudFonts.REGULAR,
				muted);
		}

		setSize(Math.round(WIDTH), Math.round(height));
	}
}
