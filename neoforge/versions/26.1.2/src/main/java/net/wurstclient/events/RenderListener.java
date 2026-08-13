/*
 * Copyright (c) 2014-2026 Wurst-Imperium and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package net.wurstclient.events;

import java.util.ArrayList;

import com.mojang.blaze3d.vertex.PoseStack;

import org.joml.Matrix4f;

import net.wurstclient.event.Event;
import net.wurstclient.event.Listener;

public interface RenderListener extends Listener
{
	public void onRender(PoseStack matrixStack, float partialTicks);
	
	public static class RenderEvent extends Event<RenderListener>
	{
		private final PoseStack matrixStack;
		private final float partialTicks;
		
		private static final Matrix4f LAST_PROJECTION = new Matrix4f();

		public RenderEvent(PoseStack matrixStack, float partialTicks,
			Matrix4f projection)
		{
			this.matrixStack = matrixStack;
			this.partialTicks = partialTicks;
			LAST_PROJECTION.set(projection);
		}

		public static Matrix4f getLastProjection()
		{
			return LAST_PROJECTION;
		}
		
		@Override
		public void fire(ArrayList<RenderListener> listeners)
		{
			for(RenderListener listener : listeners)
				listener.onRender(matrixStack, partialTicks);
		}
		
		@Override
		public Class<RenderListener> getListenerType()
		{
			return RenderListener.class;
		}
	}
}
