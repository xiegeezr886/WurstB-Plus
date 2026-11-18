package net.wurstclient.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.Test;

import net.wurstclient.util.ClientConnectionPolicy;

final class ClientConnectionMixinTest
{
	@Test
	void dispatchesEventsForClientConnection()
	{
		assertTrue(ClientConnectionPolicy.shouldDispatch(PacketFlow.CLIENTBOUND));
	}

	@Test
	void ignoresIntegratedServerConnection()
	{
		assertFalse(ClientConnectionPolicy.shouldDispatch(PacketFlow.SERVERBOUND));
	}

	@Test
	void keepsStaticMixinMethodsPrivate()
	{
		for(Method method : ClientConnectionMixin.class.getDeclaredMethods())
			if(Modifier.isStatic(method.getModifiers()))
				assertTrue(Modifier.isPrivate(method.getModifiers()),
					method::toString);
	}
}
