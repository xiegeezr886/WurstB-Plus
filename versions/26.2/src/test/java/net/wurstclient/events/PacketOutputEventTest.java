package net.wurstclient.events;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import net.wurstclient.events.PacketOutputListener.PacketOutputEvent;
import org.junit.jupiter.api.Test;

final class PacketOutputEventTest
{
	@Test
	void runsCallbacksOnlyWhenSendIsConfirmed()
	{
		AtomicInteger calls = new AtomicInteger();
		PacketOutputEvent event = new PacketOutputEvent(null);
		event.runAfterSend(calls::incrementAndGet);

		assertEquals(0, calls.get());
		event.notifySent();
		assertEquals(1, calls.get());
	}
}
