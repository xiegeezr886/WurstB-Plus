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
 * 玩家在移动 tick 里因为"正在使用物品"（吃东西、拉弓、举盾）被减速时触发。
 *
 * <p>原版在 {@code LocalPlayer#aiStep()} 里调用一次 {@code isUsingItem()} 来决定这次移动的
 * 减速；监听器把 {@code bypass} 设成 true，就是让那一次调用假装没在使用物品
 * （对应 NoSlowdown 的 "Using items"/"Blocking" 开关）。
 */
public interface ItemUseSlowdownListener extends Listener
{
	public void onItemUseSlowdown(ItemUseSlowdownEvent event);
	
	public static class ItemUseSlowdownEvent extends Event<ItemUseSlowdownListener>
	{
		private boolean bypass;
		
		public ItemUseSlowdownEvent(boolean bypass)
		{
			this.bypass = bypass;
		}
		
		public boolean isBypass()
		{
			return bypass;
		}
		
		public void setBypass(boolean bypass)
		{
			this.bypass = bypass;
		}
		
		@Override
		public void fire(ArrayList<ItemUseSlowdownListener> listeners)
		{
			for(ItemUseSlowdownListener listener : listeners)
				listener.onItemUseSlowdown(this);
		}
		
		@Override
		public Class<ItemUseSlowdownListener> getListenerType()
		{
			return ItemUseSlowdownListener.class;
		}
	}
}
