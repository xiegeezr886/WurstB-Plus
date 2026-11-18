/* GlobalSetting adapted from LeavesHack 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.wurstclient.Category;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;

/** Shared behavior for the Leaves ports, independent of this settings module's toggle. */
public final class GlobalSettingHack extends Hack {
  private final CheckboxSetting changeFov = new CheckboxSetting("ChangeFov", true),
      noBadPackets = new CheckboxSetting("NoBadPackets", true),
      packetKickFix = new CheckboxSetting("PacketKickFix", true),
      clientSwitch = new CheckboxSetting("ClientSwitch", true),
      grimRotation = new CheckboxSetting("GrimRotation", true),
      snapBack = new CheckboxSetting("SnapBack", true);
  private final EnumSetting<SwingMode>
      placeSwing = new EnumSetting<>("PlaceSwing", SwingMode.values(), SwingMode.Packet),
      attackSwing = new EnumSetting<>("AttackSwing", SwingMode.values(), SwingMode.Packet);
  private final EnumSetting<HandMode> hand =
      new EnumSetting<>("HandMode", HandMode.values(), HandMode.MainHand);

  public GlobalSettingHack() {
    super("GlobalSetting");
    setCategory(Category.MISC);
    for (Setting s :
        new Setting[] {
          changeFov,
          placeSwing,
          attackSwing,
          hand,
          noBadPackets,
          packetKickFix,
          clientSwitch,
          grimRotation,
          snapBack
        }) addSetting(s);
  }

  public boolean changeFov() {
    return changeFov.isChecked();
  }

  public boolean suppressDuplicateSlots() {
    return noBadPackets.isChecked();
  }

  public boolean packetKickFix() {
    return packetKickFix.isChecked();
  }

  public boolean clientSwitch() {
    return clientSwitch.isChecked();
  }

  public boolean fullRotation() {
    return grimRotation.isChecked();
  }

  public boolean snapBack() {
    return snapBack.isChecked();
  }

  public void swing(boolean attack) {
    if (MC.player == null) return;
    InteractionHand selected =
        hand.getSelected() == HandMode.MainHand
            ? InteractionHand.MAIN_HAND
            : InteractionHand.OFF_HAND;
    SwingMode mode = (attack ? attackSwing : placeSwing).getSelected();
    if (mode == SwingMode.Both) MC.player.swing(selected);
    else if (mode == SwingMode.Packet)
      MC.player.connection.send(new ServerboundSwingPacket(selected));
    else if (mode == SwingMode.Client) {
      MC.player.swinging = true;
      MC.player.swingTime = -1;
      MC.player.swingingArm = selected;
    }
  }

  public enum SwingMode {
    Both,
    Packet,
    Client,
    None
  }

  public enum HandMode {
    MainHand,
    OffHand
  }
}
