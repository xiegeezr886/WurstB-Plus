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
 * 询问"原版的自动跳跃还允许吗"（{@code LocalPlayer#isAutoJumpEnabled()}）。
 *
 * <p>因为注入点在方法头部、拿不到原版结果，事件初值取 {@code true}（允许）：
 * 谁想关掉自动跳跃就把它设成 false（Step 启用时、{@code .goto} 正在跑时就是这种情况）。
 */
public interface AutoJumpListener extends Listener
{
	public void onAutoJump(AutoJumpEvent event);
	
	public static class AutoJumpEvent extends Event<AutoJumpListener>
	{
		private boolean autoJumpAllowed;
		
		public AutoJumpEvent(boolean autoJumpAllowed)
		{
			this.autoJumpAllowed = autoJumpAllowed;
		}
		
		public boolean isAutoJumpAllowed()
		{
			return autoJumpAllowed;
		}
		
		public void setAutoJumpAllowed(boolean autoJumpAllowed)
		{
			this.autoJumpAllowed = autoJumpAllowed;
		}
		
		@Override
		public void fire(ArrayList<AutoJumpListener> listeners)
		{
			for(AutoJumpListener listener : listeners)
				listener.onAutoJump(this);
		}
		
		@Override
		public Class<AutoJumpListener> getListenerType()
		{
			return AutoJumpListener.class;
		}
	}
}
