/*
 * Copyright (c) 2025 Penguin
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
/* Packet filters and field logging adapted from LeavesHack 13daf96.
 * Adapted portions: SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.wurstclient.Category;
import net.wurstclient.SearchTags;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.events.PacketOutputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.util.ChatUtils;

@SearchTags({"packet log", "network debug", "packet spy"})
public final class PacketLoggerHack extends Hack
    implements PacketInputListener, PacketOutputListener {
  private final CheckboxSetting logReceived = new CheckboxSetting("Log received", true);
  private final CheckboxSetting logSent = new CheckboxSetting("Log sent", true);
  private final CheckboxSetting showData = new CheckboxSetting("Show data", true);

  private int packetCount;
  private long lastReset;
  private final Map<Class<?>, CheckboxSetting> packetTypes = new LinkedHashMap<>();
  private final CheckboxSetting otherPackets = new CheckboxSetting("Other packets", false);

  public PacketLoggerHack() {
    super("PacketLogger");
    setCategory(Category.MISC);
    addSetting(logReceived);
    addSetting(logSent);
    addSetting(showData);
    filter("move-full", ServerboundMovePlayerPacket.PosRot.class);
    filter("move-position", ServerboundMovePlayerPacket.Pos.class);
    filter("move-look", ServerboundMovePlayerPacket.Rot.class);
    filter("move-ground", ServerboundMovePlayerPacket.StatusOnly.class);
    filter("vehicle-move", ServerboundMoveVehiclePacket.class);
    filter("player-action", ServerboundPlayerActionPacket.class);
    filter("update-slot", ServerboundSetCarriedItemPacket.class);
    filter("hand-swing", ServerboundSwingPacket.class);
    filter("pong", ServerboundPongPacket.class);
    filter("interact-entity", ServerboundInteractPacket.class);
    filter("interact-block", ServerboundUseItemOnPacket.class);
    filter("interact-item", ServerboundUseItemPacket.class);
    filter("close-screen", ServerboundContainerClosePacket.class);
    filter("client-command", ServerboundPlayerCommandPacket.class);
    filter("client-status", ServerboundClientCommandPacket.class);
    filter("click-slot", ServerboundContainerClickPacket.class);
    filter("pick-inventory", ServerboundPickItemPacket.class);
    filter("teleport-confirm", ServerboundAcceptTeleportationPacket.class);
    filter("s2c-velocity", ClientboundSetEntityMotionPacket.class);
    addSetting(otherPackets);
  }

  private void filter(String name, Class<?> type) {
    CheckboxSetting setting = new CheckboxSetting(name, true);
    packetTypes.put(type, setting);
    addSetting(setting);
  }

  @Override
  protected void onEnable() {
    EVENTS.add(PacketInputListener.class, this);
    EVENTS.add(PacketOutputListener.class, this);
    packetCount = 0;
    lastReset = System.currentTimeMillis();
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(PacketInputListener.class, this);
    EVENTS.remove(PacketOutputListener.class, this);
  }

  @Override
  public void onReceivedPacket(PacketInputEvent event) {
    if (!logReceived.isChecked()) return;
    logPacket("<-", event.getPacket());
  }

  @Override
  public void onSentPacket(PacketOutputEvent event) {
    if (!logSent.isChecked()) return;
    logPacket("->", event.getPacket());
  }

  private void logPacket(String dir, Packet<?> packet) {
    CheckboxSetting filter = packetTypes.get(packet.getClass());
    if (filter == null ? !otherPackets.isChecked() : !filter.isChecked()) return;
    packetCount++;
    if (packetCount > 100) {
      long elapsed = System.currentTimeMillis() - lastReset;
      if (elapsed < 1000) return;
      packetCount = 0;
      lastReset = System.currentTimeMillis();
    }

    String msg =
        dir
            + " "
            + packet.getClass().getSimpleName()
            + (showData.isChecked() ? " " + describe(packet) : "");
    MC.execute(() -> ChatUtils.message(msg));
  }

  private static String describe(Packet<?> packet) {
    if (packet instanceof ServerboundMovePlayerPacket p)
      return "x="
          + p.getX(0)
          + " y="
          + p.getY(0)
          + " z="
          + p.getZ(0)
          + " yaw="
          + p.getYRot(0)
          + " pitch="
          + p.getXRot(0)
          + " ground="
          + p.isOnGround();
    if (packet instanceof ServerboundPlayerActionPacket p)
      return p.getAction() + " " + p.getPos().toShortString() + " " + p.getDirection();
    if (packet instanceof ServerboundSetCarriedItemPacket p) return "slot=" + p.getSlot();
    if (packet instanceof ServerboundSwingPacket p) return "hand=" + p.getHand();
    if (packet instanceof ServerboundUseItemOnPacket p)
      return p.getHitResult().getBlockPos().toShortString() + " " + p.getHitResult().getDirection();
    if (packet instanceof ServerboundUseItemPacket p) return "hand=" + p.getHand();
    if (packet instanceof ServerboundContainerClickPacket p)
      return "slot=" + p.getSlotNum() + " button=" + p.getButtonNum();
    if (packet instanceof ServerboundAcceptTeleportationPacket p) return "id=" + p.getId();
    if (packet instanceof ClientboundSetEntityMotionPacket p)
      return "id=" + p.getId() + " velocity=" + p.getXa() + "," + p.getYa() + "," + p.getZa();
    if (packet instanceof ServerboundPlayerCommandPacket p) return "action=" + p.getAction();
    if (packet instanceof ServerboundClientCommandPacket p) return "action=" + p.getAction();
    if (packet instanceof ServerboundContainerClosePacket p)
      return "container=" + p.getContainerId();
    if (packet instanceof ServerboundPongPacket p) return "id=" + p.getId();
    if (packet instanceof ServerboundPickItemPacket p) return "slot=" + p.getSlot();
    if (packet instanceof ServerboundMoveVehiclePacket p)
      return "position=" + p.getX() + "," + p.getY() + "," + p.getZ();
    return "";
  }
}
