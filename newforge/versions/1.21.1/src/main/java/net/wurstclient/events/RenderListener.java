package net.wurstclient.events;

import java.util.ArrayList;

import com.mojang.blaze3d.vertex.PoseStack;

import net.wurstclient.event.Event;
import net.wurstclient.event.Listener;
import net.wurstclient.util.render.RenderScope;

public interface RenderListener extends Listener
{
	public void onRender(PoseStack matrixStack, float partialTicks);

	public static class RenderEvent extends Event<RenderListener>
	{
		private final PoseStack matrixStack;
		private final float partialTicks;

		public RenderEvent(PoseStack matrixStack, float partialTicks)
		{
			this.matrixStack = matrixStack;
			this.partialTicks = partialTicks;
		}

		@Override
		public void fire(ArrayList<RenderListener> listeners)
		{
			for(RenderListener listener : listeners)
				try(RenderScope ignored = RenderScope.capture())
				{
					listener.onRender(matrixStack, partialTicks);
				}
		}

		@Override
		public Class<RenderListener> getListenerType()
		{
			return RenderListener.class;
		}
	}
}
