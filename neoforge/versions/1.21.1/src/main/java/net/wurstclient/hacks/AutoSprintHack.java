/* SprintPlus behavior from LeavesHack/Meteor Client. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.CheckboxSetting;
import net.wurstclient.settings.EnumSetting;

public final class AutoSprintHack extends Hack {
  private final EnumSetting<Mode> mode =
      new EnumSetting<>("sprint-mode", Mode.values(), Mode.Strict);
  private final CheckboxSetting water = new CheckboxSetting("unsprint-in-water", true);
  private final CheckboxSetting stationary = new CheckboxSetting("sprint-while-stationary", false);

  public AutoSprintHack() {
    super("AutoSprint");
    setCategory(Category.MOVEMENT);
    addSetting(mode);
    addSetting(water);
    addSetting(stationary);
  }

  public void applySprint() {
    if (!isEnabled() || MC.player == null) return;
    if (water.isChecked() && MC.player.isInWater()) {
      MC.player.setSprinting(false);
      return;
    }
    MC.player.setSprinting(shouldSprint());
  }

  private boolean shouldSprint() {
    if (WURST.getHax().fireworkElytraFlyHack.isEnabled()) return false;
    float movement =
        mode.getSelected() == Mode.Rage
            ? Math.abs(MC.player.zza) + Math.abs(MC.player.xxa)
            : MC.player.zza;
    if (movement <= (MC.player.isUnderWater() ? 1E-5F : 0.8F)
        && mode.getSelected() == Mode.Rage
        && !stationary.isChecked()) return false;
    boolean strict =
        MC.player.getFoodData().getFoodLevel() > 6
            && (!MC.player.horizontalCollision || MC.player.minorHorizontalCollision);
    return mode.getSelected() == Mode.Rage || strict;
  }

  @Override
  protected void onDisable() {
    if (MC.player != null && !MC.options.keySprint.isDown()) MC.player.setSprinting(false);
  }

  public boolean shouldOmniSprint() {
    return isEnabled() && mode.getSelected() == Mode.Rage;
  }

  public boolean shouldSprintHungry() {
    return isEnabled() && mode.getSelected() == Mode.Rage;
  }

  private enum Mode {
    Strict,
    Rage
  }
}
