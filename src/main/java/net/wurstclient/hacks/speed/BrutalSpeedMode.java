/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.speed;

import net.minecraft.client.player.LocalPlayer;
import net.wurstclient.hacks.SpeedHackHack;

/**
 * Brutal：和 NCP Bhop 同一套动作，但目标速度取 {@code max(目标速度, Speed × 0.45)}、
 * 空中强度给满 1。
 */
public final class BrutalSpeedMode implements SpeedMode
{
	@Override
	public String getName()
	{
		return "Brutal";
	}
	
	@Override
	public void apply(SpeedHackHack hack, LocalPlayer player, float forward,
		float sideways, double targetSpeed)
	{
		hack.applyHop(player, forward, sideways,
			Math.max(targetSpeed, hack.getSpeedSetting() * 0.45), 0.42, 1);
	}
}
