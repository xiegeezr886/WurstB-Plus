/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.wurstclient.WurstClient;

/** A reversible, synchronous inventory action, scoped to its original player. */
public final class InventorySwap implements AutoCloseable {
  private final LocalPlayer player;
  private final int previous, source;
  private boolean closed;

  private InventorySwap(int source) {
    player = WurstClient.MC.player;
    previous = player.getInventory().selected;
    this.source = source;
    if (source >= 9) swap();
    else player.getInventory().selected = source;
    LeavesSlots.INSTANCE.select(player.getInventory().selected);
  }

  public static InventorySwap acquire(int source) {
    var mc = WurstClient.MC;
    if (source < 0
        || source >= 36
        || mc.player == null
        || mc.gameMode == null
        || mc.player.containerMenu != mc.player.inventoryMenu
        || !mc.player.inventoryMenu.getCarried().isEmpty()) return null;
    return new InventorySwap(source);
  }

  private void swap() {
    WurstClient.MC.gameMode.handleInventoryMouseClick(
        player.inventoryMenu.containerId, source, previous, ClickType.SWAP, player);
  }

  @Override
  public void close() {
    if (closed) return;
    closed = true;
    if (WurstClient.MC.player != player || WurstClient.MC.gameMode == null) return;
    if (source >= 9 && player.containerMenu == player.inventoryMenu) swap();
    player.getInventory().selected = previous;
    LeavesSlots.INSTANCE.select(previous);
  }
}
