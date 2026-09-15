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
 * 原版在 {@code LocalPlayer#isStayingOnGroundSurface()} 里询问"玩家是不是应该被
 * 边缘夹住"（SafeWalk / ScaffoldWalk 就是靠它生效的）。监听器可以把结果改成 {@code true}。
 */
public interface StayingOnGroundSurfaceListener extends Listener
{
	public void onStayingOnGroundSurface(StayingOnGroundSurfaceEvent event);
	
	public static class StayingOnGroundSurfaceEvent
		extends Event<StayingOnGroundSurfaceListener>
	{
		private boolean stayingOnGroundSurface;
		private final boolean normallyStayingOnGroundSurface;
		
		public StayingOnGroundSurfaceEvent(boolean stayingOnGroundSurface)
		{
			this.stayingOnGroundSurface = stayingOnGroundSurface;
			normallyStayingOnGroundSurface = stayingOnGroundSurface;
		}
		
		public boolean isStayingOnGroundSurface()
		{
			return stayingOnGroundSurface;
		}
		
		public void setStayingOnGroundSurface(boolean stayingOnGroundSurface)
		{
			this.stayingOnGroundSurface = stayingOnGroundSurface;
		}
		
		public boolean isNormallyStayingOnGroundSurface()
		{
			return normallyStayingOnGroundSurface;
		}
		
		@Override
		public void fire(ArrayList<StayingOnGroundSurfaceListener> listeners)
		{
			for(StayingOnGroundSurfaceListener listener : listeners)
				listener.onStayingOnGroundSurface(this);
		}
		
		@Override
		public Class<StayingOnGroundSurfaceListener> getListenerType()
		{
			return StayingOnGroundSurfaceListener.class;
		}
	}
}
