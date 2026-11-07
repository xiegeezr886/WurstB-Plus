import java.lang.reflect.Method;
import java.util.ArrayList;

import net.wurstclient.event.Event;
import net.wurstclient.event.Listener;
import net.wurstclient.event.WurstSubscriber;

/** Checks that a private annotated-style handler can be bound and invoked. */
public class PrivateSubscribeTest
{
	public static class TestEvent extends Event<TestListener>
	{
		int hits;

		@Override
		public void fire(ArrayList<TestListener> listeners)
		{
			for(TestListener listener : listeners)
				listener.on(this);
		}

		@Override
		public Class<TestListener> getListenerType()
		{
			return TestListener.class;
		}
	}

	public interface TestListener extends Listener
	{
		void on(TestEvent event);
	}

	private static final class Hidden
	{
		private void privateHandler(TestEvent event)
		{
			event.hits++;
		}
	}

	public static void main(String[] args) throws Exception
	{
		Hidden hidden = new Hidden();
		Method method =
			Hidden.class.getDeclaredMethod("privateHandler", TestEvent.class);
		WurstSubscriber subscriber =
			new WurstSubscriber(hidden, method, TestEvent.class);

		TestEvent event = new TestEvent();
		subscriber.callSubscriber(event);
		subscriber.callSubscriber(event);

		if(event.hits != 2)
			throw new AssertionError("expected 2 calls, got " + event.hits);

		System.out.println("private handler invoked " + event.hits + " times");
	}
}
