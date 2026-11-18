/* BreakManager adapted from LeavesHack 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.events.*;

/** Tracks each player's primary and secondary mining positions on the client thread. */
public final class BreakTracker implements PacketInputListener, UpdateListener {
  public static final BreakTracker INSTANCE = new BreakTracker();
  private final Map<Integer, Entry> primary = new LinkedHashMap<>(),
      secondary = new LinkedHashMap<>();
  private Object world;

  private BreakTracker() {}

  public void register() {
    var events = WurstClient.INSTANCE.getEventManager();
    events.add(PacketInputListener.class, this);
    events.add(UpdateListener.class, this);
  }

  public Collection<Entry> primary() {
    return Collections.unmodifiableCollection(primary.values());
  }

  public Collection<Entry> secondary() {
    return Collections.unmodifiableCollection(secondary.values());
  }

  public Entry primary(int player) {
    return primary.get(player);
  }

  @Override
  public void onReceivedPacket(PacketInputEvent event) {
    if (!(event.getPacket() instanceof ClientboundBlockDestructionPacket packet)) return;
    var mc = WurstClient.MC;
    var captured = mc.level;
    mc.execute(
        () -> {
          if (captured != null && mc.level == captured) accept(packet);
        });
  }

  private void accept(ClientboundBlockDestructionPacket packet) {
    var mc = WurstClient.MC;
    if (world != mc.level) {
      primary.clear();
      secondary.clear();
      world = mc.level;
    }
    if (mc.player == null || !(mc.level.getEntity(packet.getId()) instanceof Player player)) return;
    BlockPos pos = packet.getPos().immutable();
    if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 64) return;
    long now = System.currentTimeMillis();
    int stage = packet.getProgress();
    if (stage >= 0 && stage != 255) {
      Entry second = secondary.get(packet.getId());
      if (stage != 0) {
        if (!unbreakable(pos)) secondary.put(packet.getId(), new Entry(pos, packet.getId(), now));
        return;
      }
      if (second != null && second.pos.equals(pos) && now - second.started < 150000) return;
    }
    Entry old = primary.get(packet.getId());
    if (old != null && !old.failed && old.pos.equals(pos)) return;
    primary.put(packet.getId(), new Entry(pos, packet.getId(), now));
    if (!secondary.containsKey(packet.getId()) && !unbreakable(pos))
      secondary.put(packet.getId(), new Entry(pos, packet.getId(), now));
  }

  @Override
  public void onUpdate() {
    var mc = WurstClient.MC;
    if (world != mc.level) {
      primary.clear();
      secondary.clear();
      world = mc.level;
    }
    if (mc.player == null || mc.level == null) return;
    long now = System.currentTimeMillis();
    primary.values().removeIf(e -> e.player() == null || now - e.started > 150000);
    secondary
        .values()
        .removeIf(
            e ->
                e.player() == null
                    || mc.level.isEmptyBlock(e.pos)
                    || now - e.started > Math.max(2000, e.breakTime * 2));
    for (Entry e : primary.values()) {
      e.breakTime = breakTime(e.pos);
      if (unbreakable(e.pos)) {
        e.failed = true;
        e.complete = false;
      } else if (mc.level.isEmptyBlock(e.pos)) {
        e.complete = true;
        e.failed = false;
      } else if (!e.complete && now - e.started > e.breakTime * 1.5) e.failed = true;
    }
  }

  public boolean isMining(BlockPos pos, boolean includeSelf) {
    var mc = WurstClient.MC;
    if (mc.player == null || mc.level == null) return false;
    var mine = WurstClient.INSTANCE.getHax().packetMineHack;
    if (includeSelf
        && mine.isEnabled()
        && (pos.equals(mine.getTargetPos()) || pos.equals(mine.getSecondPos()))) return true;
    return primary.values().stream()
        .anyMatch(
            e ->
                !e.failed
                    && e.player() != null
                    && e.pos.equals(pos)
                    && e.player().getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) <= 49);
  }

  private static boolean unbreakable(BlockPos pos) {
    var mc = WurstClient.MC;
    float h = mc.level.getBlockState(pos).getDestroySpeed(mc.level, pos);
    return h < 0 || h == 100;
  }

  private static double breakTime(BlockPos pos) {
    var mc = WurstClient.MC;
    var state = mc.level.getBlockState(pos);
    float hardness = state.getDestroySpeed(mc.level, pos);
    if (hardness < 0) return Double.POSITIVE_INFINITY;
    double best = 0;
    for (int i = 0; i < 9; i++) {
      var stack = mc.player.getInventory().getItem(i);
      double speed = stack.getDestroySpeed(state);
      int efficiency =
          EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY, stack);
      if (speed > 1 && efficiency > 0) speed += efficiency * efficiency + 1;
      double progress =
          hardness == 0
              ? 1
              : speed
                  / hardness
                  / (!state.requiresCorrectToolForDrops() || stack.isCorrectToolForDrops(state)
                      ? 30
                      : 100);
      best = Math.max(best, progress);
    }
    return Math.max(50, 50 / best);
  }

  public static final class Entry {
    public final BlockPos pos;
    public final int id;
    public final long started;
    public double breakTime;
    public boolean failed, complete;

    Entry(BlockPos pos, int id, long now) {
      this.pos = pos;
      this.id = id;
      started = now;
      breakTime = breakTime(pos);
    }

    public Player player() {
      var world = WurstClient.MC.level;
      return world != null && world.getEntity(id) instanceof Player p ? p : null;
    }

    public double progress(double multiplier) {
      return failed || complete
          ? 1
          : Math.min(
              1, (System.currentTimeMillis() - started) / Math.max(50, breakTime * multiplier));
    }
  }
}
