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
 * 玩家移动被"悬崖边收缩"修正时触发（原版在
 * {@code LocalPlayer#maybeBackOffFromEdge} 里做这件事）。
 *
 * <p>事件只做通知、不可取消：原版已经算完了修正结果，监听器要做的是
 * "知道玩家正贴着边缘"这件事（SafeWalk 用它来决定要不要显示潜行）。
 */
public interface ClipAtLedgeListener extends Listener
{
	public void onClipAtLedge(ClipAtLedgeEvent event);
	
	public static class ClipAtLedgeEvent extends Event<ClipAtLedgeListener>
	{
		private final boolean clipping;
		
		public ClipAtLedgeEvent(boolean clipping)
		{
			this.clipping = clipping;
		}
		
		/** 玩家这一步的移动是否真的被边缘收缩修正过。 */
		public boolean isClipping()
		{
			return clipping;
		}
		
		@Override
		public void fire(ArrayList<ClipAtLedgeListener> listeners)
		{
			for(ClipAtLedgeListener listener : listeners)
				listener.onClipAtLedge(this);
		}
		
		@Override
		public Class<ClipAtLedgeListener> getListenerType()
		{
			return ClipAtLedgeListener.class;
		}
	}
}
