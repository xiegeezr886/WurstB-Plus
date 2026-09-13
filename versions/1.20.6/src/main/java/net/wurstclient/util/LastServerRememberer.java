/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.util;

import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.wurstclient.WurstClient;
import net.wurstclient.mixinterface.IMultiplayerScreen;

/**
 * Remembers the last server you joined to make the "重新连接",
 * "自动重连" and "上次服务器" buttons work.
 */
public enum LastServerRememberer
{
	;
	
	private static ServerData lastServer;
	
	public static ServerData getLastServer()
	{
		return lastServer;
	}
	
	public static void setLastServer(ServerData server)
	{
		lastServer = server;
	}
	
	public static void joinLastServer(JoinMultiplayerScreen mpScreen)
	{
		if(lastServer == null)
			return;
		
		((IMultiplayerScreen)mpScreen).connectToServer(lastServer);
	}
	
	public static void reconnect(Screen prevScreen)
	{
		if(lastServer == null)
			return;
		
		ConnectScreen.startConnecting(prevScreen, WurstClient.MC,
			ServerAddress.parseString(lastServer.ip), lastServer, false);
	}
}
