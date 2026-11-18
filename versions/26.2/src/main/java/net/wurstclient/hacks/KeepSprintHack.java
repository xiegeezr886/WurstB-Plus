/* Epsilon KeepSprint Vanilla/Legit modes with NeoRaven slowdown controls. GPL-3.0. */
package net.wurstclient.hacks;

import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.events.PostMotionListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class KeepSprintHack extends Hack implements PostMotionListener {
  private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.VANILLA);
  private final SliderSetting slow =
      new SliderSetting("Slow", 0, 0, 1, .01, ValueDisplay.PERCENTAGE);
  private final CheckboxSetting air = new CheckboxSetting("Disable while jumping", false);
  private boolean restart;
  private Object owner, world;

  public KeepSprintHack() {
    super("KeepSprint");
    setCategory(Category.COMBAT);
    addSetting(mode);
    addSetting(slow);
    addSetting(air);
  }

  protected void onEnable() {
    restart = false;
    EVENTS.add(PostMotionListener.class, this);
  }

  protected void onDisable() {
    restart = false;
    owner = null;
    EVENTS.remove(PostMotionListener.class, this);
  }

  public boolean shouldKeepSprint(Player p) {
    if (p != MC.player || !p.isSprinting()) return false;
    if (!isEnabled()) return WURST.getHax().killauraHack.shouldKeepSprint();
    if (air.isChecked() && !p.onGround()) return false;
    if (mode.getSelected() == Mode.LEGIT) {
      restart = true;
      owner = p;
      world = MC.level;
      return false;
    }
    return true;
  }

  public double motionMultiplier(double vanilla) {
    return isEnabled() ? 1 - slow.getValue() : 1;
  }

  public void onPostMotion() {
    if (restart) {
      restart = false;
      if (owner == MC.player
          && world == MC.level
          && MC.player != null
          && MC.gui.screen() == null
          && MC.player.getFoodData().getFoodLevel() > 6
          && !MC.player.isBlocking()
          && (MC.player.input.getMoveVector().y != 0 || MC.player.input.getMoveVector().x != 0))
        MC.player.setSprinting(true);
    }
  }

  public enum Mode {
    VANILLA,
    LEGIT
  }
}
