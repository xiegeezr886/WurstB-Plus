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

public interface HitResultRayTraceListener extends Listener
{
	public void onHitResultRayTrace(HitResultRayTraceEvent event);
	
	public static class HitResultRayTraceEvent
		extends Event<HitResultRayTraceListener>
	{
		private final float partialTicks;
		private boolean includeFluids;
		
		public HitResultRayTraceEvent(float partialTicks)
		{
			this.partialTicks = partialTicks;
		}

		public float getPartialTicks()
		{
			return partialTicks;
		}

		public boolean shouldIncludeFluids()
		{
			return includeFluids;
		}

		public void includeFluids()
		{
			includeFluids = true;
		}
		
		@Override
		public void fire(ArrayList<HitResultRayTraceListener> listeners)
		{
			for(HitResultRayTraceListener listener : listeners)
				listener.onHitResultRayTrace(this);
		}
		
		@Override
		public Class<HitResultRayTraceListener> getListenerType()
		{
			return HitResultRayTraceListener.class;
		}
	}
}
