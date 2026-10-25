/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import java.text.DecimalFormat;

import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.hud2.SimpleHUDMod;

public final class PitchDisplayMod extends SimpleHUDMod
{
	private static final DecimalFormat FORMAT = new DecimalFormat("0.##");

	public PitchDisplayMod()
	{
		super("PitchDisplay", "Pitch Display", "Shows the player's pitch.");
		setTranslationKey("pitch_display");
		setDefaultPosition(180, 630);
	}

	@Override
	public String getText()
	{
		return "Pitch: " + (MC.player == null ? "0"
			: FORMAT.format(MC.player.getXRot()));
	}

	@Override
	public String getIcon()
	{
		return HudIcon.MOVE;
	}
}
