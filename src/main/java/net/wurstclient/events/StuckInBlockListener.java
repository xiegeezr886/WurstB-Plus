/*
 * Copyright (c) 2014-2025 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.events;

import java.util.ArrayList;
import net.minecraft.world.level.block.state.BlockState;
import net.wurstclient.event.CancellableEvent;
import net.wurstclient.event.Listener;

/**
 * 实体被方块"粘住"时触发（缠网、细雪、甜浆果丛都会走
 * {@code Entity#makeStuckInBlock}）。取消掉就不会被粘住。
 */
public interface StuckInBlockListener extends Listener
{
	public void onStuckInBlock(StuckInBlockEvent event);
	
	public static class StuckInBlockEvent
		extends CancellableEvent<StuckInBlockListener>
	{
		private final BlockState state;
		
		public StuckInBlockEvent(BlockState state)
		{
			this.state = state;
		}
		
		public BlockState getState()
		{
			return state;
		}
		
		@Override
		public void fire(ArrayList<StuckInBlockListener> listeners)
		{
			for(StuckInBlockListener listener : listeners)
			{
				listener.onStuckInBlock(this);
				
				if(isCancelled())
					break;
			}
		}
		
		@Override
		public Class<StuckInBlockListener> getListenerType()
		{
			return StuckInBlockListener.class;
		}
	}
}
