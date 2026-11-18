/* Epsilon packet-delay adapter, GPL-3.0. */
package net.wurstclient.epsilon;

import java.util.ArrayDeque;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.wurstclient.WurstClient;

/** Bounded FIFO. Callers serialize offer/drain; replay is always on the client. */
public final class EpsilonPacketQueue {
  private record Entry(
      Packet<?> packet, long time, ClientLevel world, ClientPacketListener connection) {}

  private final ArrayDeque<Entry> queue = new ArrayDeque<>();
  private final OrderedReplay<Entry> replay =
      new OrderedReplay<>(
          task -> WurstClient.MC.execute(task),
          entry -> {
            var mc = WurstClient.MC;
            if (entry.world != null
                && entry.world == mc.level
                && entry.connection != null
                && entry.connection == mc.getConnection()) apply(entry);
          });

  public synchronized int size() {
    return queue.size();
  }

  public synchronized void clear() {
    queue.clear();
  }

  public synchronized void offer(Packet<?> packet, long now) {
    if (queue.size() >= 2048) flush(Long.MAX_VALUE);
    var mc = WurstClient.MC;
    queue.addLast(new Entry(packet, now, mc.level, mc.getConnection()));
  }

  public synchronized void flush(long deadline) {
    var pending = new java.util.ArrayList<Entry>();
    while (!queue.isEmpty() && queue.peekFirst().time <= deadline) pending.add(queue.removeFirst());
    if (pending.isEmpty()) return;
    replay.submit(pending);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static void apply(Entry entry) {
    ((Packet) entry.packet).handle(entry.connection);
  }
}
