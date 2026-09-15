/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.hacks.criticals;

import net.wurstclient.hacks.CriticalsHack;

/** Packet：按选定的 Packet profile 发包。 */
public final class PacketCriticalsMode implements CriticalsMode
{
	@Override
	public String getName()
	{
		return "Packet";
	}
	
	@Override
	public boolean doCriticals(CriticalsHack hack)
	{
		hack.sendPacketProfile(hack.getPacketProfile());
		return true;
	}
}
