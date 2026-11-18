/* NeoRaven HitboxModule multiplier and target gates, GPL-3.0. */
package net.wurstclient.hacks;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.wurstclient.Category;
import net.wurstclient.epsilon.EpsilonTargets;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class HitboxesHack extends Hack {
  private final SliderSetting multiplier =
      new SliderSetting("Multiplier", 5, 0, 30, .5, ValueDisplay.DECIMAL);
  private final CheckboxSetting players = new CheckboxSetting("Players only", true),
      weapon = new CheckboxSetting("Weapon only", false);

  public HitboxesHack() {
    super("Hitboxes");
    setCategory(Category.COMBAT);
    addSetting(multiplier);
    addSetting(players);
    addSetting(weapon);
  }

  public float getExtraSize(Entity entity) {
    if (!isEnabled()
        || !entity.level().isClientSide()
        || !EpsilonTargets.valid(entity)
        || entity.isInvisible()
        || MC.gui.screen() != null
        || MC.player.isUsingItem()
        || players.isChecked() && !(entity instanceof Player)
        || weapon.isChecked() && !EpsilonTargets.weapon()) return 0;
    // 1.8.9 living-entity collision border is .1; 1.20 uses a zero pick radius.
    return .1F * multiplier.getValueF();
  }
}
