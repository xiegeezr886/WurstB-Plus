package net.wurstclient.hud2;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.wurstclient.WurstClient;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.WorldChangeListener;

public final class ClientMetricsManager
	implements PacketInputListener, WorldChangeListener
{
	private static final double SMOOTHING = 0.2;
	private static final double DEFAULT_TPS = 20;
	private volatile long lastTimePacketNanos;
	private volatile double ticksPerSecond = DEFAULT_TPS;

	public void start()
	{
		WurstClient.INSTANCE.getEventManager().add(PacketInputListener.class,
			this);
		WurstClient.INSTANCE.getEventManager().add(WorldChangeListener.class,
			this);
	}

	public void stop()
	{
		WurstClient.INSTANCE.getEventManager().remove(PacketInputListener.class,
			this);
		WurstClient.INSTANCE.getEventManager().remove(WorldChangeListener.class,
			this);
	}

	@Override
	public void onReceivedPacket(PacketInputEvent event)
	{
		if(!(event.getPacket() instanceof ClientboundSetTimePacket))
			return;

		long now = System.nanoTime();
		long previous = lastTimePacketNanos;
		lastTimePacketNanos = now;
		if(previous == 0)
			return;

		double sample = calculateTps(now - previous);
		ticksPerSecond += (sample - ticksPerSecond) * SMOOTHING;
	}

	@Override
	public void onWorldChange(ClientLevel world)
	{
		lastTimePacketNanos = 0;
		ticksPerSecond = DEFAULT_TPS;
	}

	public double getTicksPerSecond()
	{
		return ticksPerSecond;
	}

	static double calculateTps(long intervalNanos)
	{
		if(intervalNanos <= 0)
			return DEFAULT_TPS;
		double seconds = intervalNanos / 1_000_000_000D;
		return Math.max(0, Math.min(DEFAULT_TPS, 20D / seconds));
	}
}
