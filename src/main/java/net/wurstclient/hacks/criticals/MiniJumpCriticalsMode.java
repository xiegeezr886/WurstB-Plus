/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.criticals;

import net.wurstclient.WurstClient;
import net.wurstclient.hacks.CriticalsHack;

/** Mini jump：给玩家一个很小的向上速度，再补位移动包。 */
public final class MiniJumpCriticalsMode implements CriticalsMode
{
	@Override
	public String getName()
	{
		return "Mini jump";
	}
	
	@Override
	public boolean requiresGround()
	{
		return true;
	}
	
	@Override
	public boolean doCriticals(CriticalsHack hack)
	{
		if(!WurstClient.MC.player.onGround())
			return false;
		
		double height = hack.getJumpHeight();
		WurstClient.MC.player.push(0, height, 0);
		WurstClient.MC.player.fallDistance = 0.1F;
		WurstClient.MC.player.setOnGround(false);
		hack.sendOffset(height, false);
		hack.sendOffset(0.000001, false);
		return true;
	}
}
