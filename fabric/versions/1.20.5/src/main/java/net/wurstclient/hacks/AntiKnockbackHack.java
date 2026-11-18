/* Epsilon Velocity Cancel/Reduce/Delay port, GPL-3.0. */
package net.wurstclient.hacks;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.InteractionHand;
import net.wurstclient.Category;
import net.wurstclient.epsilon.*;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixin.ClientboundExplodePacketMixin;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.*;

public final class AntiKnockbackHack extends Hack
    implements PacketInputListener,
        UpdateListener,
        MoveInputListener,
        VelocityFromFluidListener,
        net.wurstclient.opal.VelocityMode {
  private enum Mode {
    CANCEL,
    REDUCE,
    DELAY
  }

  private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.CANCEL);
  private final CheckboxSetting motion = new CheckboxSetting("Server motion", true),
      explosion = new CheckboxSetting("Explosion", true);
  private final CheckboxSetting waterPush = new CheckboxSetting("No water push", true),
      entityPush = new CheckboxSetting("No entity push", true),
      blockPush = new CheckboxSetting("No block push", true);
  private final CheckboxSetting jumpReset = new CheckboxSetting("Jump reset", false),
      swing = new CheckboxSetting("Swing hand", false);
  private final SliderSetting attackCount =
      new SliderSetting("Attack counts", 1, 1, 5, 1, ValueDisplay.INTEGER);
  private final SliderSetting sprintTicks =
      new SliderSetting("Sprint ticks", 3, 1, 5, 1, ValueDisplay.INTEGER);
  private final SliderSetting maxDelay =
      new SliderSetting("Max delay", 1000, 0, 1000, 50, ValueDisplay.INTEGER);
  private final SliderSetting delayTicks =
      new SliderSetting("Delay ticks", 3, 1, 5, 1, ValueDisplay.INTEGER);
  private final EpsilonPacketQueue packets = new EpsilonPacketQueue();
  private Object player, world, connection;
  private Mode previous;
  private volatile boolean delaying;
  private volatile int attacks;
  private int ticks, sprints;
  private long started, lag = -10000;
  private float yaw;
  private boolean jump;
  private RotationQueue rotations;

  private static long now() {
    return System.nanoTime() / 1_000_000;
  }

  public AntiKnockbackHack() {
    super("AntiKnockback");
    setCategory(Category.COMBAT);
    for (Setting s :
        new Setting[] {
          mode, motion, explosion, waterPush, entityPush, blockPush,
          attackCount, sprintTicks, maxDelay, swing, delayTicks, jumpReset
        }) addSetting(s);
    for (Setting s : new Setting[] {motion, explosion, waterPush, entityPush, blockPush})
      s.visibleWhen(() -> mode.getSelected() == Mode.CANCEL);
    for (Setting s : new Setting[] {attackCount, sprintTicks, maxDelay, swing})
      s.visibleWhen(() -> mode.getSelected() == Mode.REDUCE);
    for (Setting s : new Setting[] {delayTicks, jumpReset})
      s.visibleWhen(() -> mode.getSelected() == Mode.DELAY);
  }

  protected synchronized void onEnable() {
    reset();
    rotations = new RotationQueue(RotationQueue.Priority.COMBAT);
    rotations.start();
    EVENTS.add(PacketInputListener.class, this);
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(MoveInputListener.class, this);
    EVENTS.add(VelocityFromFluidListener.class, this);
  }

  protected synchronized void onDisable() {
    EVENTS.remove(PacketInputListener.class, this);
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(MoveInputListener.class, this);
    EVENTS.remove(VelocityFromFluidListener.class, this);
    packets.flush(Long.MAX_VALUE);
    rotations.stop();
    reset();
  }

  private void reset() {
    packets.clear();
    delaying = false;
    attacks = ticks = sprints = 0;
    jump = false;
    lag = -10000;
    player = MC.player;
    world = MC.level;
    connection = MC.getConnection();
    previous = mode.getSelected();
  }

  private boolean sameSession() {
    return player == MC.player && world == MC.level && connection == MC.getConnection();
  }

  public boolean blocksBacktrack() {
    return isEnabled() && (delaying || attacks > 0);
  }

  public boolean isAttacking() {
    return attacks > 0;
  }

  public boolean preventsEntityPush() {
    return isEnabled() && mode.getSelected() == Mode.CANCEL && entityPush.isChecked();
  }

  public boolean preventsBlockPush() {
    return isEnabled() && mode.getSelected() == Mode.CANCEL && blockPush.isChecked();
  }

  @Override
  public void onVelocityFromFluid(VelocityFromFluidEvent event) {
    if (isEnabled()
        && mode.getSelected() == Mode.CANCEL
        && waterPush.isChecked()
        && MC.player != null
        && event.getEntity() == MC.player) event.cancel();
  }

  public synchronized void onUpdate() {
    if (!sameSession()) {
      reset();
      rotations.clear();
    }
    if (previous != mode.getSelected()) {
      packets.flush(Long.MAX_VALUE);
      reset();
      rotations.clear();
    }
    if (!EpsilonTargets.ready()) {
      packets.flush(Long.MAX_VALUE);
      reset();
      rotations.clear();
      return;
    }
    if (mode.getSelected() == Mode.DELAY && delaying && --ticks <= 0) {
      delaying = false;
      packets.flush(Long.MAX_VALUE);
    }
    if (mode.getSelected() == Mode.REDUCE) {
      if (delaying
          && (MC.player.onGround()
              || now() - lag < 100
              || now() - started >= maxDelay.getValueI())) {
        delaying = false;
        packets.flush(Long.MAX_VALUE);
        jump = true;
        if (now() - lag >= 100) {
          attacks = attackCount.getValueI();
          sprints = sprintTicks.getValueI();
        }
      }
      if (attacks > 0) {
        var hit =
            EpsilonTargets.ray(3, new Rotation(MC.player.getYRot(), MC.player.getXRot()), false);
        if (hit != null
            && hit.getEntity() instanceof net.minecraft.world.entity.player.Player target
            && EpsilonTargets.valid(target)
            && MC.screen == null) {
          MC.gameMode.attack(MC.player, target);
          if (swing.isChecked()) MC.player.swing(InteractionHand.MAIN_HAND);
        }
        attacks--;
      }
    }
  }

  public synchronized void onReceivedPacket(PacketInputEvent event) {
    if (!sameSession()) {
      reset();
      return;
    }
    if (MC.player == null) return;
    Packet<?> packet = event.getPacket();
    if (packet instanceof ClientboundDisconnectPacket
        || packet instanceof ClientboundRespawnPacket
        || packet instanceof ClientboundLoginPacket) {
      reset();
      return;
    }
    if (packet instanceof ClientboundPlayerPositionPacket
        || packet instanceof ClientboundExplodePacket) {
      lag = now();
      if (delaying) {
        delaying = false;
        packets.flush(Long.MAX_VALUE);
      }
    }
    if (mode.getSelected() == Mode.CANCEL) {
      if (motion.isChecked()
          && packet instanceof ClientboundSetEntityMotionPacket p
          && p.getId() == MC.player.getId()) {
        event.cancel();
        return;
      }
      if (explosion.isChecked() && packet instanceof ClientboundExplodePacket p) {
        var access = (ClientboundExplodePacketMixin) (Object) p;
        access.wurst_setKnockbackX(0); access.wurst_setKnockbackY(0); access.wurst_setKnockbackZ(0);
      }
      return;
    }
    if (packet instanceof ClientboundKeepAlivePacket
        || packet instanceof ClientboundPingPacket
        || packet instanceof ClientboundPlayerPositionPacket) return;
    if (delaying) {
      if (mode.getSelected() == Mode.DELAY
          || packet instanceof ClientboundSetEntityMotionPacket
          || packet instanceof ClientboundMoveEntityPacket
          || packet instanceof ClientboundTeleportEntityPacket) {
        packets.offer(packet, now());
        event.cancel();
      }
      return;
    }
    if (packet instanceof ClientboundSetEntityMotionPacket p
        && p.getId() == MC.player.getId()
        && now() - lag >= 100
        && p.getYa() > 0
        && (p.getXa() != 0 || p.getZa() != 0)) {
      delaying = true;
      started = now();
      ticks = delayTicks.getValueI();
      yaw = (float) Math.toDegrees(Math.atan2(p.getZa(), p.getXa())) + 90;
      // Epsilon Delay lets the initial impulse through; Reduce buffers it.
      if (mode.getSelected() == Mode.REDUCE) {
        packets.offer(packet, now());
        event.cancel();
      }
    }
  }

  public synchronized void onMoveInput(MoveInputEvent event) {
    if (!EpsilonTargets.ready()) return;
    if (mode.getSelected() == Mode.DELAY
        && jumpReset.isChecked()
        && MC.player.onGround()
        && MC.player.hurtTime == 9) event.setJump(true);
    if (mode.getSelected() != Mode.REDUCE) return;
    if (jump && MC.player.onGround() && now() - lag >= 100) {
      event.setJump(true);
      jump = false;
    }
    if (sprints > 0
        && now() - lag >= 100
        && !WURST.getHax().scaffoldWalkHack.isEnabled()
        && WURST.getHax().killauraHack.getCurrentTarget() == null) {
      sprints--;
      event.setForward(1);
      event.setSideways(0);
      rotations.setRotation(new Rotation(yaw, MC.player.getXRot()));
    }
  }
}
