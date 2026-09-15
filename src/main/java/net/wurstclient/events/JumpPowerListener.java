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
 * 玩家即将起跳时触发，可以改掉这一次的跳跃力。
 *
 * <p>事件里同时带着原版算出来的基础跳跃力（{@code getBaseJumpPower()}）——
 * 跳跃提升、蜂蜜块之类都会改变它，监听器应当基于这个基准值计算，而不是假设它是个常数。
 */
public interface JumpPowerListener extends Listener
{
	public void onJumpPower(JumpPowerEvent event);
	
	public static class JumpPowerEvent extends Event<JumpPowerListener>
	{
		private final float baseJumpPower;
		private float jumpPower;
		
		public JumpPowerEvent(float baseJumpPower)
		{
			this.baseJumpPower = baseJumpPower;
			jumpPower = baseJumpPower;
		}
		
		public float getBaseJumpPower()
		{
			return baseJumpPower;
		}
		
		public float getJumpPower()
		{
			return jumpPower;
		}
		
		public void setJumpPower(float jumpPower)
		{
			this.jumpPower = jumpPower;
		}
		
		@Override
		public void fire(ArrayList<JumpPowerListener> listeners)
		{
			for(JumpPowerListener listener : listeners)
				listener.onJumpPower(this);
		}
		
		@Override
		public Class<JumpPowerListener> getListenerType()
		{
			return JumpPowerListener.class;
		}
	}
}
