/* Epsilon AimBot.AimAssist with NeoRaven weapon/mining gates. GPL-3.0. */
package net.wurstclient.hacks;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.EntityHitResult;
import net.wurstclient.Category;
import net.wurstclient.epsilon.EpsilonTargets;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.*;
import net.wurstclient.hacks.aura.OpenAura;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class AimAssistHack extends Hack implements UpdateListener {
  private final SliderSetting strength =
      new SliderSetting("Aim strength", 30, 1, 100, 1, ValueDisplay.INTEGER);
  private final SliderSetting smooth =
      new SliderSetting("Aim smooth", 45, 1, 180, 1, ValueDisplay.DEGREES);
  private final SliderSetting aimTime =
      new SliderSetting("Aim time", 2, 1, 10, 1, ValueDisplay.INTEGER);
  private final SliderSetting range = new SliderSetting("Range", 5, 1, 6, .1, ValueDisplay.DECIMAL);
  private final SliderSetting reaction =
      new SliderSetting("Reaction time", 80, 0, 500, 10, ValueDisplay.INTEGER);
  private final CheckboxSetting weapon = new CheckboxSetting("Only weapon", false);
  private final CheckboxSetting click = new CheckboxSetting("LMB activation", false);
  private final CheckboxSetting walls = new CheckboxSetting("Ignore walls", false);
  private final CheckboxSetting invis = new CheckboxSetting("Ignore invisible", false);
  private final CheckboxSetting mining = new CheckboxSetting("Stop when breaking", true);
  private float acceleration, yaw = Float.NaN;
  private int aimTicks, targetId = -1;
  private long visibleSince;
  private Object world, player;

  public AimAssistHack() {
    super("AimAssist");
    setCategory(Category.COMBAT);
    addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
    addSetting(strength);
    addSetting(smooth);
    addSetting(aimTime);
    addSetting(range);
    addSetting(reaction);
    addSetting(weapon);
    addSetting(click);
    addSetting(walls);
    addSetting(invis);
    addSetting(mining);
  }

  protected void onEnable() {
    reset();
    EVENTS.add(UpdateListener.class, this);
  }

  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    reset();
  }

  private void reset() {
    acceleration = 0;
    yaw = Float.NaN;
    aimTicks = 0;
    targetId = -1;
    visibleSince = 0;
  }

  public void onUpdate() {
    if (world != MC.level || player != MC.player) {
      reset();
      world = MC.level;
      player = MC.player;
    }
    if (!EpsilonTargets.ready()
        || MC.gui.screen() != null
        || (weapon.isChecked() && !EpsilonTargets.weapon())
        || (click.isChecked() && !MC.options.keyAttack.isDown())
        || (mining.isChecked() && MC.gameMode.isDestroying())) {
      reset();
      return;
    }
    aimTicks = MC.hitResult instanceof EntityHitResult ? aimTicks + 1 : 0;
    if (aimTicks >= aimTime.getValueI()) {
      acceleration = 0;
      return;
    }
    var targets =
        EpsilonTargets.acquire(range.getValue(), 360, 1, !invis.isChecked(), walls.isChecked());
    if (targets.isEmpty()) {
      reset();
      return;
    }
    var target = targets.get(0);
    long now = System.nanoTime() / 1000000;
    if (target.getId() != targetId) {
      targetId = target.getId();
      visibleSince = now;
      acceleration = 0;
    }
    if (!walls.isChecked() && now - visibleSince < reaction.getValue()) return;
    acceleration = Math.min(1, acceleration + strength.getValueF() / 10000F);
    if (Float.isNaN(yaw)) yaw = MC.player.getYRot();
    float desired = OpenAura.rotationTo(MC.player.getEyePosition(), target.getEyePosition()).yaw();
    float delta =
        Mth.clamp(Mth.wrapDegrees(desired - yaw), -smooth.getValueF(), smooth.getValueF());
    double gcd = Math.pow(MC.options.sensitivity().get() * .6 + .2, 3) * 1.2;
    yaw += delta - delta % gcd;
    // Normalize the upstream per-frame easing to 20 ticks to avoid FPS-dependent aim speed.
    float factor = 1 - (float) Math.pow(1 - acceleration, 3);
    MC.player.setYRot(MC.player.getYRot() + Mth.wrapDegrees(yaw - MC.player.getYRot()) * factor);
  }
}
