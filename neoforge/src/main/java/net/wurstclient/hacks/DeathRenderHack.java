/* DeathRender ported from MrBZBZ/LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.events.PacketInputListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class DeathRenderHack extends Hack implements PacketInputListener {
  private final SliderSetting range =
      new SliderSetting("Range", 12, 0, 20, 0.1, ValueDisplay.DECIMAL);

  public DeathRenderHack() {
    super("DeathRender");
    setCategory(Category.RENDER);
    addSetting(range);
  }

  @Override
  protected void onEnable() {
    EVENTS.add(PacketInputListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(PacketInputListener.class, this);
  }

  @Override
  public void onReceivedPacket(PacketInputEvent event) {
    if (!(event.getPacket() instanceof ClientboundEntityEventPacket packet)
        || packet.getEventId() != 3) return;
    var world = MC.level;
    MC.execute(
        () -> {
          if (!isEnabled() || world == null || MC.level != world || MC.player == null) return;
          var entity = packet.getEntity(world);
          if (!(entity instanceof Player)
              || entity == MC.player
              || MC.player.distanceToSqr(entity) > range.getValue() * range.getValue()) return;
          LightningBolt lightning = new LightningBolt(EntityType.LIGHTNING_BOLT, world);
          lightning.setPos(entity.position());
          lightning.setVisualOnly(true);
          int id = -2_000_000;
          while (world.getEntity(id) != null) id--;
          world.putNonPlayerEntity(id, lightning);
        });
  }
}
