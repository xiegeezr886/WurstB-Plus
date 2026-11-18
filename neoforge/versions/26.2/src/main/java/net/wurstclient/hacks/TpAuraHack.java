/* NeoRaven TPAuraModule.java, GPL-3.0. */
package net.wurstclient.hacks;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.InteractionHand;
import net.wurstclient.Category;
import net.wurstclient.epsilon.EpsilonTargets;
import net.wurstclient.events.*;
import net.wurstclient.hack.*;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class TpAuraHack extends Hack implements UpdateListener {
  private final SliderSetting range =
      new SliderSetting("Range", 4.25, 1, 6, .05, ValueDisplay.DECIMAL);
  private final CheckboxSetting weapon = new CheckboxSetting("Weapon only", true);
  private double x, y, z;
  private Object player, world;
  private int lastHurt;

  public TpAuraHack() {
    super("TP-Aura");
    setCategory(Category.COMBAT);
    addConflictGroup(HackConflictGroup.COMBAT_TARGETING);
    addSetting(range);
    addSetting(weapon);
  }

  protected void onEnable() {
    offset();
    lastHurt = 0;
    EVENTS.add(UpdateListener.class, this);
  }

  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
  }

  private void offset() {
    var random = ThreadLocalRandom.current();
    x = random.nextInt(-15, 16) / 10.;
    y = random.nextInt(10, 16) / 10.;
    z = random.nextInt(-15, 16) / 10.;
  }

  public void onUpdate() {
    if (player != MC.player || world != MC.level) {
      offset();
      lastHurt = 0;
      player = MC.player;
      world = MC.level;
    }
    if (!EpsilonTargets.ready()
        || MC.gui.screen() != null
        || MC.player.isUsingItem()
        || weapon.isChecked() && !EpsilonTargets.weapon()) return;
    if (MC.player.hurtTime > lastHurt) offset();
    lastHurt = MC.player.hurtTime;
    var targets = EpsilonTargets.acquire(range.getValue(), 360, 1, true, true);
    if (targets.isEmpty()) return;
    var target = targets.get(0);
    MC.player.setPos(target.getX() + x, target.getY() + y, target.getZ() + z);
    MC.gameMode.attack(MC.player, target);
    MC.player.swing(InteractionHand.MAIN_HAND);
  }
}
