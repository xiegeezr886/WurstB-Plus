/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.events;

import java.util.ArrayList;
import net.wurstclient.event.Event;
import net.wurstclient.event.Listener;

/**
 * 询问"这个玩家算不算旁观者"（{@code Player#isSpectator()}）。事件初值是原版的答案，
 * 监听器可以改成 true（Freecam 就是靠它让客户端把自己当成旁观者，从而不参与碰撞）。
 */
public interface IsSpectatorListener extends Listener
{
	public void onIsSpectator(IsSpectatorEvent event);
	
	public static class IsSpectatorEvent extends Event<IsSpectatorListener>
	{
		private boolean spectator;
		private final boolean normallySpectator;
		
		public IsSpectatorEvent(boolean spectator)
		{
			this.spectator = spectator;
			normallySpectator = spectator;
		}
		
		public boolean isSpectator()
		{
			return spectator;
		}
		
		public void setSpectator(boolean spectator)
		{
			this.spectator = spectator;
		}
		
		public boolean isNormallySpectator()
		{
			return normallySpectator;
		}
		
		@Override
		public void fire(ArrayList<IsSpectatorListener> listeners)
		{
			for(IsSpectatorListener listener : listeners)
				listener.onIsSpectator(this);
		}
		
		@Override
		public Class<IsSpectatorListener> getListenerType()
		{
			return IsSpectatorListener.class;
		}
	}
}
