/* Follower ported from MrBZBZ/LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.events.*;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.LeavesUtils;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.RotationUtils;

public final class FollowHack extends Hack implements UpdateListener, MoveInputListener {
  private final CheckboxSetting autoDisable = new CheckboxSetting("AutoDisable", true);
  private final SliderSetting returnTime =
      new SliderSetting("ReturnTime", 300, 0, 1000, 1, ValueDisplay.INTEGER);
  private final SliderSetting targetRange =
      new SliderSetting("TargetRange", 60, 0, 100, 1, ValueDisplay.INTEGER);
  private final SliderSetting attackRange =
      new SliderSetting("AttackRange", 3.5, 0, 6, 0.1, ValueDisplay.DECIMAL);
  private final SliderSetting speed =
      new SliderSetting("Speed", 1.7, 0, 5, 0.1, ValueDisplay.DECIMAL);
  private Player target, requested;
  private long returnUntil;
  private boolean jump, following;
  private Object world;

  public FollowHack() {
    super("Follow");
    setCategory(Category.COMBAT);
    for (Setting s : new Setting[] {autoDisable, returnTime, targetRange, attackRange, speed})
      addSetting(s);
  }

  @Override
  protected void onEnable() {
    returnUntil = 0;
    jump = following = false;
    world = MC.level;
    EVENTS.add(UpdateListener.class, this);
    EVENTS.add(MoveInputListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    EVENTS.remove(MoveInputListener.class, this);
    target = requested = null;
    jump = following = false;
  }

  public void setEntity(Entity entity) {
    requested = entity instanceof Player player ? player : null;
  }

  @Override
  public void onUpdate() {
    following = false;
    if (world != MC.level) {
      target = requested = null;
      world = MC.level;
      returnUntil = 0;
    }
    if (!LeavesUtils.ready()) return;
    if (!WURST.getHax().fireworkElytraFlyHack.isEnabled()) {
      if (autoDisable.isChecked()) setEnabled(false);
      return;
    }
    if (MC.player.onGround()) {
      jump = true;
      return;
    }
    target =
        requested != null
                && requested.isAlive()
                && MC.player.distanceTo(requested) <= targetRange.getValue()
            ? requested
            : LeavesUtils.enemy(targetRange.getValue());
    if (target == null
        || MC.options.keyUp.isDown()
        || MC.options.keyDown.isDown()
        || MC.options.keyLeft.isDown()
        || MC.options.keyRight.isDown()
        || MC.options.keyJump.isDown()
        || MC.options.keyShift.isDown()) return;
    Vec3 difference = MC.player.position().subtract(target.position());
    double distance = difference.length();
    if (distance < 1E-6) return;
    double altitude = target.getY() + difference.y * attackRange.getValue() / distance;
    if (MC.player.getY() <= Math.floor(altitude))
      returnUntil = System.nanoTime() + returnTime.getValueI() * 1_000_000L;
    var box = target.getBoundingBox();
    var eye = MC.player.getEyePosition();
    Vec3 aim =
        new Vec3(
            Mth.clamp(eye.x, box.minX, box.maxX),
            Mth.clamp(eye.y, box.minY, box.maxY),
            Mth.clamp(eye.z, box.minZ, box.maxZ));
    var rotation = RotationUtils.getNeededRotations(aim);
    float yaw = System.nanoTime() < returnUntil ? 0 : rotation.yaw(),
        pitch = System.nanoTime() < returnUntil ? -90 : rotation.pitch();
    if (!MC.player.isFallFlying()) return;
    following = true;
    WURST.getHax().fireworkElytraFlyHack.setFlightRotation(yaw, pitch);
    MC.player.setDeltaMovement(Vec3.directionFromRotation(pitch, yaw).scale(speed.getValue()));
    MC.player.connection.send(
        new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(
            yaw, pitch, MC.player.onGround()));
  }

  @Override
  public void onMoveInput(MoveInputEvent event) {
    if (jump) {
      event.setJump(true);
      jump = false;
    }
  }

  public boolean isFollowing() {
    return isEnabled() && following;
  }
}
