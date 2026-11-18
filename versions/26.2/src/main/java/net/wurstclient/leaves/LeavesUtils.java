/* Shared LeavesHack behavior adapted for Minecraft 1.20.1.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.util.*;

public final class LeavesUtils {
  private LeavesUtils() {}

  public static boolean ready() {
    var mc = WurstClient.MC;
    return mc.player != null
        && mc.level != null
        && mc.gameMode != null
        && !mc.player.isSpectator()
        && mc.gui.screen() == null
        && mc.player.containerMenu == mc.player.inventoryMenu
        && mc.player.inventoryMenu.getCarried().isEmpty();
  }

  public static Player enemy(double range) {
    var mc = WurstClient.MC;
    if (mc.player == null || mc.level == null) return null;
    return mc.level.players().stream()
        .filter(
            p ->
                p != mc.player
                    && p.isAlive()
                    && !p.isSpectator()
                    && !(p instanceof FakePlayerEntity)
                    && !WurstClient.INSTANCE.getFriends().contains(p.getScoreboardName())
                    && mc.player.distanceToSqr(p) <= range * range)
        .min(Comparator.comparingDouble(mc.player::distanceToSqr))
        .orElse(null);
  }

  public static boolean using(boolean onlyMain) {
    var mc = WurstClient.MC;
    return mc.player != null
        && (mc.options.keyUse.isDown() || mc.player.isUsingItem())
        && (!onlyMain || mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND);
  }

  public static boolean place(
      BlockPos pos, Item item, double range, boolean inventory, boolean rotate) {
    return place(pos, item, range, inventory, rotate, false);
  }

  public static boolean place(
      BlockPos pos, Item item, double range, boolean inventory, boolean rotate, boolean packet) {
    var mc = WurstClient.MC;
    if (!ready() || !mc.level.isInWorldBounds(pos) || !mc.level.getBlockState(pos).canBeReplaced())
      return false;
    var params = BlockPlacer.getBlockPlacingParams(pos);
    if (params == null || params.distanceSq() > range * range) return false;
    int slot = InventoryUtils.indexOf(item, inventory ? 36 : 9);
    try (InventorySwap swap = InventorySwap.acquire(slot)) {
      if (swap == null || !mc.player.getMainHandItem().is(item)) return false;
      boolean sneak = !mc.player.isShiftKeyDown();
      try {
        if (sneak)
          mc.player.connection.send(
              net.wurstclient.util.render.V152Input.sneak(mc.player, true));
        if (rotate) look(params.hitVec());
        boolean placed;
        if (packet) {
          WurstClient.IMC
              .getInteractionManager()
              .sendPlayerInteractBlockPacket(InteractionHand.MAIN_HAND, params.toHitResult());
          placed = true;
        } else
          placed =
              mc.gameMode
                  .useItemOn(mc.player, InteractionHand.MAIN_HAND, params.toHitResult())
                  .consumesAction();
        if (placed) {
          WurstClient.INSTANCE.getHax().globalSettingHack.swing(false);
          WurstClient.INSTANCE.getHax().placeRenderHack.record(pos, false);
        }
        return placed;
      } finally {
        if (sneak)
          mc.player.connection.send(
              net.wurstclient.util.render.V152Input.sneak(mc.player, false));
        if (rotate) restoreLook();
      }
    }
  }

  public static void look(Vec3 target) {
    var r = RotationUtils.getNeededRotations(target);
    rotate(r.yaw(), r.pitch());
  }

  public static void rotate(float yaw, float pitch) {
    var p = WurstClient.MC.player;
    if (WurstClient.INSTANCE.getHax().globalSettingHack.fullRotation())
      p.connection.send(
          new ServerboundMovePlayerPacket.PosRot(
              p.getX(), p.getY(), p.getZ(), yaw, pitch, p.onGround(), net.wurstclient.WurstClient.MC.player.horizontalCollision));
    else p.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, p.onGround(), net.wurstclient.WurstClient.MC.player.horizontalCollision));
  }

  public static void restoreLook() {
    if (!WurstClient.INSTANCE.getHax().globalSettingHack.snapBack()) return;
    var p = WurstClient.MC.player;
    p.connection.send(new ServerboundMovePlayerPacket.Rot(p.getYRot(), p.getXRot(), p.onGround(), net.wurstclient.WurstClient.MC.player.horizontalCollision));
  }
}
