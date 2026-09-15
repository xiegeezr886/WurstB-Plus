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

/** NCP Bhop：地面用目标速度，空中按 0.35 强度平滑。 */
public final class NcpBhopSpeedMode implements SpeedMode
{
	@Override
	public String getName()
	{
		return "NCP Bhop";
	}
	
	@Override
	public void apply(SpeedHackHack hack, LocalPlayer player, float forward,
		float sideways, double targetSpeed)
	{
		hack.applyHop(player, forward, sideways, targetSpeed, 0.42, 0.35);
	}
}
