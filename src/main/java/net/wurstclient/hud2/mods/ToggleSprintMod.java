/*
 * Copyright (c) 2026 WurstB+ Plus contributors
 *
 * This source code is subject to the terms of the GNU Affero General Public
 * License, version 3. If a copy of the AGPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/agpl-3.0.txt
 */
// Ported from Soar Client v7.1.2 (LGPL-3.0).
package net.wurstclient.hud2.mods;

import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.hud2.SimpleHUDMod;

public final class ToggleSprintMod extends SimpleHUDMod
{
	public ToggleSprintMod()
	{
		super("ToggleSprint", "Toggle Sprint",
			"Shows whether the player is sprinting.");
		setTranslationKey("toggle_sprint");
		setDefaultPosition(10, 606);
	}

	@Override
	public String getText()
	{
		if(MC.player == null)
			return "Walking";
		return MC.player.isSprinting() ? "Sprinting" : "Walking";
	}

	@Override
	public String getIcon()
	{
		return HudIcon.ACTIVITY;
	}
}
