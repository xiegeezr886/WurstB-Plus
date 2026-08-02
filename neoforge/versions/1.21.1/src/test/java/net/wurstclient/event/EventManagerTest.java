package net.wurstclient.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import net.wurstclient.WurstClient;

final class EventManagerTest
{
	@Test
	void annotatedSubscriptionsUseTargetIdentity()
	{
		EventManager manager = new EventManager(WurstClient.INSTANCE);
		Subscriber first = new Subscriber();
		Subscriber second = new Subscriber();
		manager.subscribeAnnotated(first);
		manager.subscribeAnnotated(first);
		manager.subscribeAnnotated(second);

		assertEquals(2,
			manager.getAnnotatedSubscriberCount(TestEvent.class));
		manager.unsubscribeAnnotated(first);
		assertEquals(1,
			manager.getAnnotatedSubscriberCount(TestEvent.class));
	}

	@Test
	void findsInheritedSubscriberMethods()
	{
		EventManager manager = new EventManager(WurstClient.INSTANCE);
		manager.subscribeAnnotated(new InheritedSubscriber());
		assertEquals(1,
			manager.getAnnotatedSubscriberCount(TestEvent.class));
	}

	private interface TestListener extends Listener
	{
		void onTest();
	}

	private static final class TestEvent extends Event<TestListener>
	{
		@Override
		public void fire(ArrayList<TestListener> listeners)
		{
			for(TestListener listener : listeners)
				listener.onTest();
		}

		@Override
		public Class<TestListener> getListenerType()
		{
			return TestListener.class;
		}
	}

	private static class Subscriber
	{
		@WurstSubscribe
		public void onTest(TestEvent event)
		{}
	}

	private static final class InheritedSubscriber extends Subscriber
	{
	}
}
