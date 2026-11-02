/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import net.minecraft.util.Mth;
import net.wurstclient.hud2.HUDMod;
import net.wurstclient.hud2.nanovg.HudFonts;

public final class GodbridgeAssistMod extends HUDMod
{
	public GodbridgeAssistMod()
	{
		super("GodbridgeAssist", "Godbridge Assist",
			"Shows the alignment used for godbridging.");
		setTranslationKey("godbridge_assist");
		setDefaultPosition(350, 490);
	}

	@Override
	protected void draw()
	{
		float yaw = MC.player == null ? 0
			: Math.abs(Mth.wrapDegrees(MC.player.getYRot()) % 90);
		float pitch = MC.player == null ? 0 : MC.player.getXRot();
		boolean aligned = yaw < 5 && pitch > 70;

		String[] lines = {String.format("Yaw: %.1f", yaw),
			String.format("Pitch: %.1f", pitch),
			aligned ? "Aligned" : "Not aligned"};
		float width = 110;
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
