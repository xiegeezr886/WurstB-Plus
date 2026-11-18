/* Epsilon TriggerBot crosshair ray and cooldown/critical gates. GPL-3.0. */
package net.wurstclient.hacks;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.wurstclient.Category;
import net.wurstclient.epsilon.EpsilonTargets;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.*;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.Rotation;

public final class TriggerBotHack extends Hack implements UpdateListener {
  private final SliderSetting range = new SliderSetting("Range", 3, 1, 7, .1, ValueDisplay.DECIMAL);
  private final SliderSetting min =
      new SliderSetting("Random delay min", 2, 0, 20, 1, ValueDisplay.INTEGER);
  private final SliderSetting max =
      new SliderSetting("Random delay max", 13, 0, 20, 1, ValueDisplay.INTEGER);
  private final SliderSetting cooldown =
      new SliderSetting("Attack cooldown", .9, .5, 1, .05, ValueDisplay.PERCENTAGE);
  private final SliderSetting fall =
      new SliderSetting("Crit fall distance", 0, 0, 1, .05, ValueDisplay.DECIMAL);
  private final CheckboxSetting weapon = new CheckboxSetting("Weapon only", false);
  private int delay;
  private Object player, world;

  public TriggerBotHack() {
    super("TriggerBot");
    setCategory(Category.COMBAT);
    addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
    addSetting(range);
    addSetting(min);
    addSetting(max);
    addSetting(cooldown);
    addSetting(fall);
    addSetting(weapon);
  }

  protected void onEnable() {
    delay = 0;
    EVENTS.add(UpdateListener.class, this);
  }

  protected void onDisable() {
    delay = 0;
    EVENTS.remove(UpdateListener.class, this);
  }

  public void onUpdate() {
    if (player != MC.player || world != MC.level) {
      delay = 0;
      player = MC.player;
      world = MC.level;
    }
    if (!EpsilonTargets.ready() || MC.gui.screen() != null || MC.player.isUsingItem()) {
      delay = 0;
      return;
    }
    if (delay > 0) {
      delay--;
      return;
    }
    if (weapon.isChecked() && !EpsilonTargets.weapon()) return;
    var hit =
        EpsilonTargets.ray(
            range.getValue(), new Rotation(MC.player.getYRot(), MC.player.getXRot()), false);
    if (hit == null || !EpsilonTargets.valid(hit.getEntity()) || !shouldAttack()) return;
    MC.gameMode.attack(MC.player, hit.getEntity());
    MC.player.swing(InteractionHand.MAIN_HAND);
    delay =
        ThreadLocalRandom.current()
            .nextInt(
                Math.min(min.getValueI(), max.getValueI()),
                Math.max(min.getValueI(), max.getValueI()) + 1);
  }

  private boolean shouldAttack() {
    if (MC.player.fallDistance > 1 && MC.player.fallDistance < 1.14) return false;
    if (MC.player.getAttackStrengthScale(0) < (MC.player.onGround() ? 1 : cooldown.getValue()))
      return false;
    if (!MC.options.keyJump.isDown() || MC.player.isInWater() || MC.player.isInLava()) return true;
    boolean canCrit =
        !MC.player.getAbilities().flying
            && !MC.player.isFallFlying()
            && !MC.player.hasEffect(MobEffects.BLINDNESS)
            && !MC.player.onClimbable()
            && !MC.player.isPassenger();
    return !canCrit || !MC.player.onGround() && MC.player.fallDistance > fall.getValue();
  }
}
