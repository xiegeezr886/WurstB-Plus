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
 * 下界传送门把玩家卷进去时触发（原版 {@code LocalPlayer#handleNetherPortalClient()}
 * 里那次 {@code updateNausea()} 之前）。
 *
 * <p>原版这一步会把当前界面关掉；监听器把 {@code keepScreen} 设成 true，
 * 就是要求"先别关，等我用完"（mixin 会临时把当前界面藏起来，等
 * {@code updateNausea()} 跑完再放回去），PortalGUI 就是这么工作的。
 */
public interface PortalNauseaListener extends Listener
{
	public void onPortalNausea(PortalNauseaEvent event);
	
	public static class PortalNauseaEvent
		extends Event<PortalNauseaListener>
	{
		private boolean keepScreen;
		
		public PortalNauseaEvent(boolean keepScreen)
		{
			this.keepScreen = keepScreen;
		}
		
		public boolean shouldKeepScreen()
		{
			return keepScreen;
		}
		
		public void setKeepScreen(boolean keepScreen)
		{
			this.keepScreen = keepScreen;
		}
		
		@Override
		public void fire(ArrayList<PortalNauseaListener> listeners)
		{
			for(PortalNauseaListener listener : listeners)
				listener.onPortalNausea(this);
		}
		
		@Override
		public Class<PortalNauseaListener> getListenerType()
		{
			return PortalNauseaListener.class;
		}
	}
}
