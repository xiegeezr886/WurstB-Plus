import java.util.ArrayList;

import net.wurstclient.event.Event;
import net.wurstclient.event.EventManager;
import net.wurstclient.event.Listener;
import net.wurstclient.event.WurstSubscribe;

/** Checks override dedupe and private-handler registration in EventManager. */
public class EventOverrideTest
{
	public interface TestListener extends Listener
	{}

	public static class TestEvent extends Event<TestListener>
	{
		@Override
		public void fire(ArrayList<TestListener> listeners)
		{}

		@Override
		public Class<TestListener> getListenerType()
		{
			return TestListener.class;
		}
	}

	public static class Base
	{
		@WurstSubscribe
		void onEvent(TestEvent event)
		{}
	}

	public static class Derived extends Base
	{
		@Override
		@WurstSubscribe
		void onEvent(TestEvent event)
		{}
	}

	public static class PrivateHandler
	{
		@WurstSubscribe
		private void onPrivate(TestEvent event)
		{}
	}

	public static void main(String[] args)
	{
		EventManager manager = new EventManager(null);

		manager.subscribeAnnotated(new Derived());
		int overridden = manager.getAnnotatedSubscriberCount(TestEvent.class);
		if(overridden != 1)
			throw new AssertionError(
				"override should register once, got " + overridden);

		manager.subscribeAnnotated(new PrivateHandler());
		int withPrivate = manager.getAnnotatedSubscriberCount(TestEvent.class);
		if(withPrivate != 2)
			throw new AssertionError(
				"private handler not registered, count " + withPrivate);

		System.out.println("override count=1, private handler registered, total="
			+ withPrivate);
	}
}
