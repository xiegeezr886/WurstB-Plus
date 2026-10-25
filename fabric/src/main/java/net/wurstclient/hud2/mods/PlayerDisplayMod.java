/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.compat.HudCompat;
import net.wurstclient.hud2.nanovg.HudFonts;

public final class PlayerDisplayMod extends HUDMod
{
	public PlayerDisplayMod()
	{
		super("PlayerDisplay", "Player Display",
			"Shows the player's head and name.");
		setTranslationKey("player_display");
		setDefaultPosition(180, 480);
	}

	@Override
	protected void draw()
	{
		if(MC.player == null)
			return;

		drawBackground(150, 40);
		drawPlayerHead(MC.player.getSkinTextureLocation().toString(), 6, 6,
			28, 28, 6);
		drawText(HudCompat.playerName(), 42, 6, 10, HudFonts.DEMIBOLD);
		drawText(HudCompat.health() + " HP", 42, 20, 9,
			HudFonts.REGULAR, getFontColor(210));
		setSize(150, 40);
	}
}
