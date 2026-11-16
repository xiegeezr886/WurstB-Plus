/* NeoRaven ReachModule.java range and input gates, GPL-3.0.
 * Existing 1.20.1 selection hooks are retained; block reach stays vanilla. */
package net.wurstclient.hacks;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.EntityHitResult;
import net.wurstclient.Category;
import net.wurstclient.epsilon.EpsilonTargets;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.SliderSetting;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.Rotation;

public final class ReachHack extends Hack {
  private final SliderSetting min = new SliderSetting("Min", 3, 1, 6, .05, ValueDisplay.DECIMAL);
  private final SliderSetting max = new SliderSetting("Max", 3.2, 1, 6, .05, ValueDisplay.DECIMAL);
  private final CheckboxSetting weapon = new CheckboxSetting("Weapon only", true);
  private final CheckboxSetting moving = new CheckboxSetting("Moving only", false);
  private final CheckboxSetting sprint = new CheckboxSetting("Sprint only", false);
  private final CheckboxSetting walls = new CheckboxSetting("Hit through blocks", false);
  private Object player, world;
  private int tick = -1;
  private double sampled;

  public ReachHack() {
    super("Reach");
    setCategory(Category.PLAYER);
    addSetting(min);
    addSetting(max);
    addSetting(weapon);
    addSetting(moving);
    addSetting(sprint);
    addSetting(walls);
  }

  private boolean active() {
    return isEnabled()
        && EpsilonTargets.ready()
        && MC.screen == null
        && (!weapon.isChecked() || EpsilonTargets.weapon())
        && (!moving.isChecked()
            || MC.player.input.forwardImpulse != 0
            || MC.player.input.leftImpulse != 0)
        && (!sprint.isChecked() || MC.player.isSprinting());
  }

  protected void onEnable() {
    tick = -1;
  }

  public double changeEntityRange(double original) {
    if (!active()) return original;
    // Share a sample across all renderer hooks in a tick to avoid inconsistent trimming.
    if (player != MC.player || world != MC.level || tick != MC.player.tickCount) {
      player = MC.player;
      world = MC.level;
      tick = MC.player.tickCount;
      double low = Math.min(min.getValue(), max.getValue()),
          high = Math.max(min.getValue(), max.getValue());
      sampled = low == high ? low : ThreadLocalRandom.current().nextDouble(low, high);
    }
    return sampled;
  }

  public double changeBlockRange(double original) {
    return original;
  }

  public float getReachDistance() {
    return Math.max(getEntityRange(), getBlockRange());
  }

  public float getEntityRange() {
    return (float) changeEntityRange(MC.gameMode != null && MC.gameMode.hasInfiniteItems() ? 6 : 3);
  }

  public float getBlockRange() {
    return MC.gameMode != null && MC.gameMode.hasInfiniteItems() ? 5 : 4.5F;
  }

  public void updatePick() {
    if (!active()) return;
    EntityHitResult hit =
        EpsilonTargets.ray(
            getEntityRange(),
            new Rotation(MC.player.getYRot(), MC.player.getXRot()),
            walls.isChecked());
    if (hit == null
        || !(hit.getEntity() instanceof LivingEntity || hit.getEntity() instanceof ItemFrame))
      return;
    MC.hitResult = hit;
    MC.crosshairPickEntity = hit.getEntity();
  }
}
