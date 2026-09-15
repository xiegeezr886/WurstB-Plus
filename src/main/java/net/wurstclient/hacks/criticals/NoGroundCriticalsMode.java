/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.criticals;

import net.wurstclient.hacks.CriticalsHack;

/** NoGround：只补一个极小的向下位移动包，让服务端认为玩家离地。 */
public final class NoGroundCriticalsMode implements CriticalsMode
{
	@Override
	public String getName()
	{
		return "NoGround";
	}
	
	@Override
	public boolean doCriticals(CriticalsHack hack)
	{
		hack.sendOffset(-0.000001, false);
		return true;
	}
}
