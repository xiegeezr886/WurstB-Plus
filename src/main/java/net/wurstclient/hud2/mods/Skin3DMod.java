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

public final class Skin3DMod extends HUDMod
{
	public Skin3DMod()
	{
		super("Skin3D", "Skin 3D", "Shows a large preview of the player's skin.");
		setTranslationKey("skin_3d");
		setDefaultPosition(350, 560);
	}

	@Override
	protected void draw()
	{
		if(MC.player == null)
			return;

		drawBackground(96, 96);
		drawPlayerHead(MC.player.getSkinTextureLocation().toString(), 12, 10,
			72, 72, 10);
		drawCenteredText(HudCompat.playerName(), 48, 84, 8,
			HudFonts.REGULAR);
		setSize(96, 96);
	}
}
