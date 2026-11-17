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

import net.minecraft.util.Mth;
import net.wurstclient.hud2.nanovg.HudIcon;
import net.wurstclient.hud2.SimpleHUDMod;

public final class YawDisplayMod extends SimpleHUDMod
{
	private static final DecimalFormat FORMAT = new DecimalFormat("0.##");

	public YawDisplayMod()
	{
		super("YawDisplay", "Yaw Display", "Shows the player's yaw.");
		setTranslationKey("yaw_display");
		setDefaultPosition(180, 652);
	}

	@Override
	public String getText()
	{
		if(MC.player == null)
			return "Yaw: 0";
		float yaw = Mth.wrapDegrees(MC.player.getYRot());
		if(yaw < 0)
			yaw += 360;
		return "Yaw: " + FORMAT.format(yaw);
	}

	@Override
	public String getIcon()
	{
		return HudIcon.MOVE;
	}
}
