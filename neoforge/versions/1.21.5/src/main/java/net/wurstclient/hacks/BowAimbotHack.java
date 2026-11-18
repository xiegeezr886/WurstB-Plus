/* Epsilon AimBot.BowAim 1.20.1 adaptation. GPL-3.0. */
package net.wurstclient.hacks;

import java.util.Comparator;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.Category;
import net.wurstclient.epsilon.*;
import net.wurstclient.events.PreMotionListener;
import net.wurstclient.hack.*;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.*;

public final class BowAimbotHack extends Hack implements PreMotionListener {
  private final SliderSetting range =
      new SliderSetting("Range", 64, 8, 128, 1, ValueDisplay.INTEGER);
  private final SliderSetting fov = new SliderSetting("FOV", 90, 10, 360, 1, ValueDisplay.DEGREES);
  private final SliderSetting predict =
      new SliderSetting("Predict ticks", 2, 0, 20, 1, ValueDisplay.INTEGER);
  private final SliderSetting speed =
      new SliderSetting("Rotation speed", 30, 5, 100, 1, ValueDisplay.DEGREES);
  private final CheckboxSetting walls = new CheckboxSetting("Ignore walls", false);
  private final CheckboxSetting client = new CheckboxSetting("Client rotation", false);
  private RotationQueue queue;
  private Object player, world;

  public BowAimbotHack() {
    super("BowAimbot");
    setCategory(Category.COMBAT);
    addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
    addSetting(range);
    addSetting(fov);
    addSetting(predict);
    addSetting(speed);
    addSetting(walls);
    addSetting(client);
  }

  protected void onEnable() {
    queue = new RotationQueue(RotationQueue.Priority.COMBAT);
    queue.start();
    EVENTS.add(PreMotionListener.class, this);
  }

  protected void onDisable() {
    EVENTS.remove(PreMotionListener.class, this);
    queue.stop();
    queue = null;
  }

  public void onPreMotion() {
    if (player != MC.player || world != MC.level) {
      queue.clear();
      player = MC.player;
      world = MC.level;
    }
    if (!EpsilonTargets.ready()
        || MC.screen != null
        || !(MC.player.getUseItem().getItem() instanceof BowItem)) {
      queue.clear();
      return;
    }
    var target =
        EpsilonTargets.acquire(range.getValue(), fov.getValue(), 1, true, walls.isChecked())
            .stream()
            .min(Comparator.comparingDouble(e -> EpsilonTargets.yaw(e) * MC.player.distanceTo(e)))
            .orElse(null);
    if (target == null) {
      queue.clear();
      return;
    }
    Vec3 point =
        target
            .getEyePosition()
            .add(
                (target.getX() - target.xo) * predict.getValue(),
                (target.getY() - target.yo) * predict.getValue(),
                (target.getZ() - target.zo) * predict.getValue());
    Vec3 d = point.subtract(MC.player.getEyePosition());
    float pitch =
        EpsilonAim.projectilePitch(
            Math.hypot(d.x, d.z), d.y, BowItem.getPowerForTime(MC.player.getTicksUsingItem()) * 3);
    if (!Float.isFinite(pitch)) {
      queue.clear();
      return;
    }
    Rotation desired = new Rotation((float) Math.toDegrees(Math.atan2(d.z, d.x)) - 90, pitch);
    Rotation from =
        queue.getLastSent() == null
            ? new Rotation(MC.player.getYRot(), MC.player.getXRot())
            : queue.getLastSent();
    Rotation next = EpsilonAim.step(from, desired, speed.getValue());
    queue.setRotation(next);
    if (client.isChecked()) {
      MC.player.setYRot(next.yaw());
      MC.player.setXRot(next.pitch());
    }
  }
}
