/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.events;

import java.util.ArrayList;
import net.minecraft.client.player.Input;
import net.wurstclient.event.Event;
import net.wurstclient.event.Listener;

/**
 * 询问"这次移动算不算有前进输入"（原版
 * {@code Input#hasForwardImpulse()}，只在 {@code LocalPlayer#aiStep()} 里用来决定冲刺）。
 *
 * <p>事件初值是原版的答案；监听器可以覆盖它（AutoSprint 的
 * "Omnidirectional Sprint" 就是在这里把"任意方向的移动"也算成前进）。
 */
public interface ForwardImpulseListener extends Listener
{
	public void onForwardImpulse(ForwardImpulseEvent event);
	
	public static class ForwardImpulseEvent
		extends Event<ForwardImpulseListener>
	{
		private final Input input;
		private boolean forwardImpulse;
		
		public ForwardImpulseEvent(Input input, boolean forwardImpulse)
		{
			this.input = input;
			this.forwardImpulse = forwardImpulse;
		}
		
		public Input getInput()
		{
			return input;
		}
		
		public boolean hasForwardImpulse()
		{
			return forwardImpulse;
		}
		
		public void setForwardImpulse(boolean forwardImpulse)
		{
			this.forwardImpulse = forwardImpulse;
		}
		
		@Override
		public void fire(ArrayList<ForwardImpulseListener> listeners)
		{
			for(ForwardImpulseListener listener : listeners)
				listener.onForwardImpulse(this);
		}
		
		@Override
		public Class<ForwardImpulseListener> getListenerType()
		{
			return ForwardImpulseListener.class;
		}
	}
}
