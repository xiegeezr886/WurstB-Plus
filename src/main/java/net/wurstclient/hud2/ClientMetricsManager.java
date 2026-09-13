package net.wurstclient.hud2;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.wurstclient.WurstClient;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.WorldChangeListener;

import net.wurstclient.util.TpsCompensation;

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
	
	/**
	 * 把"按 20 TPS 写的毫秒延迟"换算成当前 tick 速率下应该等的毫秒。
	 *
	 * <p>
	 * 本工程一直在测 TPS，但此前只有 HUD 显示在用；这才是有节奏逻辑的 hack
	 * 应该调的那个口子（换算本身在 {@code util/TpsCompensation} 里，是纯函数）。
	 * 服务端掉到 10 TPS 时同样"等 500 毫秒"只等于 5 个服务端 tick，所以这里
	 * 会把它拉长成 1000 毫秒——与参考项目 {@code delay*(20/tickRate)} 一致。
	 */
	public double getCompensatedMillis(double millis)
	{
		return TpsCompensation.scaleMillis(millis, ticksPerSecond);
	}
	
	/** 这个毫秒延迟在当前 tick 速率下相当于多少个服务端 tick。 */
	public double getTicksFor(double millis)
	{
		return TpsCompensation.ticksFor(millis, ticksPerSecond);
	}

	static double calculateTps(long intervalNanos)
	{
		if(intervalNanos <= 0)
			return DEFAULT_TPS;
		double seconds = intervalNanos / 1_000_000_000D;
		return Math.max(0, Math.min(DEFAULT_TPS, 20D / seconds));
	}
}
