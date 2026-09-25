package net.wurstclient.events;

import java.util.ArrayList;

import net.wurstclient.event.Event;
import net.wurstclient.event.Listener;

public interface MouseButtonListener extends Listener
{
	void onMouseButton(int button, int action);

	final class MouseButtonEvent extends Event<MouseButtonListener>
	{
		private final int button;
		private final int action;

		public MouseButtonEvent(int button, int action)
		{
			this.button = button;
			this.action = action;
		}

		@Override
		public void fire(ArrayList<MouseButtonListener> listeners)
		{
			for(MouseButtonListener listener : listeners)
				listener.onMouseButton(button, action);
		}

		@Override
		public Class<MouseButtonListener> getListenerType()
		{
			return MouseButtonListener.class;
		}
	}
}
