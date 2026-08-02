package net.wurstclient.util;

import net.minecraft.network.protocol.PacketFlow;

public final class ClientConnectionPolicy
{
	private ClientConnectionPolicy() {}

	public static boolean shouldDispatch(PacketFlow receiving)
	{
		return receiving == PacketFlow.CLIENTBOUND;
	}
}
