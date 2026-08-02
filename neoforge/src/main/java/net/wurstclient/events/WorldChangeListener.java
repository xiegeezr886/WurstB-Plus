package net.wurstclient.events;

import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.wurstclient.event.Event;
import net.wurstclient.event.Listener;

public interface WorldChangeListener extends Listener
{
	public void onWorldChange(@Nullable ClientLevel world);

	public static class WorldChangeEvent extends Event<WorldChangeListener>
	{
		private final @Nullable ClientLevel world;

		public WorldChangeEvent(@Nullable ClientLevel world)
		{
			this.world = world;
		}

		@Override
		public void fire(ArrayList<WorldChangeListener> listeners)
		{
			for(WorldChangeListener listener : listeners)
				listener.onWorldChange(world);
		}

		@Override
		public Class<WorldChangeListener> getListenerType()
		{
			return WorldChangeListener.class;
		}
	}
}
