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

/** Jump：真的跳一下（客户端本地跳跃 + 对应位移动包）。 */
public final class JumpCriticalsMode implements CriticalsMode
{
	@Override
	public String getName()
	{
		return "Jump";
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
		
		WurstClient.MC.player.jumpFromGround();
		hack.sendOffset(0.42, false);
		hack.sendOffset(0.000001, false);
		return true;
	}
}
