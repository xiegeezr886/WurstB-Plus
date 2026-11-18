/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.wurstclient.WurstClient;
import net.wurstclient.events.PacketOutputListener;

/** Tracks confirmed outgoing slot changes; duplicate suppression applies only to Leaves callers. */
public final class LeavesSlots implements PacketOutputListener {
  public static final LeavesSlots INSTANCE = new LeavesSlots();
  private Object connection;
  private int serverSlot = -1;

  private LeavesSlots() {}

  public int displaySlot(int fallback) {
    refresh();
    var hax = WurstClient.INSTANCE.getHax();
    return hax != null && hax.globalSettingHack.clientSwitch() && serverSlot >= 0
        ? serverSlot
        : fallback;
  }

  public void register() {
    WurstClient.INSTANCE.getEventManager().add(PacketOutputListener.class, this);
  }

  private void refresh() {
    Object current = WurstClient.MC.getConnection();
    if (current != connection) {
      connection = current;
      serverSlot = -1;
    }
  }

  public void select(int slot) {
    refresh();
    var mc = WurstClient.MC;
    if (mc.player == null || slot < 0 || slot > 8) return;
    if (WurstClient.INSTANCE.getHax().globalSettingHack.suppressDuplicateSlots()
        && serverSlot == slot) return;
    mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
  }

  @Override
  public void onSentPacket(PacketOutputEvent event) {
    if (event.getPacket() instanceof ServerboundSetCarriedItemPacket packet)
      event.runAfterSend(
          () -> {
            refresh();
            serverSlot = packet.getSlot();
          });
  }
}
