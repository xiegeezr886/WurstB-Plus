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
 * 询问"饿着肚子也能开始冲刺吗"（原版
 * {@code LocalPlayer#hasEnoughFoodToStartSprinting()}）。监听器把
 * {@code canSprintHungry} 设成 true 就能无视饥饿值开始冲刺（AutoSprint 的 "Hungry Sprint"）。
 */
public interface SprintHungerListener extends Listener
{
	public void onSprintHunger(SprintHungerEvent event);
	
	public static class SprintHungerEvent
		extends Event<SprintHungerListener>
	{
		private boolean canSprintHungry;
		
		public SprintHungerEvent(boolean canSprintHungry)
		{
			this.canSprintHungry = canSprintHungry;
		}
		
		public boolean canSprintHungry()
		{
			return canSprintHungry;
		}
		
		public void setCanSprintHungry(boolean canSprintHungry)
		{
			this.canSprintHungry = canSprintHungry;
		}
		
		@Override
		public void fire(ArrayList<SprintHungerListener> listeners)
		{
			for(SprintHungerListener listener : listeners)
				listener.onSprintHunger(this);
		}
		
		@Override
		public Class<SprintHungerListener> getListenerType()
		{
			return SprintHungerListener.class;
		}
	}
}
