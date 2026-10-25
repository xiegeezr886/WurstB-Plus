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
import net.wurstclient.hud2.nanovg.HudFonts;

public final class AppleSkinMod extends HUDMod
{
	public AppleSkinMod()
	{
		super("AppleSkin", "AppleSkin",
			"Shows hunger, saturation and exhaustion.");
		setTranslationKey("apple_skin");
		setDefaultPosition(180, 820);
	}

	@Override
	protected void draw()
	{
		int food = MC.player == null ? 0
			: MC.player.getFoodData().getFoodLevel();
		float saturation = MC.player == null ? 0
			: MC.player.getFoodData().getSaturationLevel();
		float exhaustion = MC.player == null ? 0
			: MC.player.getFoodData().getExhaustionLevel();

		String[] lines = {String.format("Food: %d / 20", food),
			String.format("Saturation: %.1f", saturation),
			String.format("Exhaustion: %.2f", exhaustion)};

		float width = 130;
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
