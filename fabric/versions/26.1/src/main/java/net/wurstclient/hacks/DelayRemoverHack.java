/* NeoRaven DelayRemoverModule, GPL-3.0. */
package net.wurstclient.hacks;

import net.wurstclient.Category;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.mixin.LivingEntityAccessor;
import net.wurstclient.settings.CheckboxSetting;

public final class DelayRemoverHack extends Hack implements UpdateListener {
  private final CheckboxSetting hitreg = new CheckboxSetting("1.7 hitreg", true);
  private final CheckboxSetting jump = new CheckboxSetting("Remove jump ticks", true);

  public DelayRemoverHack() {
    super("DelayRemover");
    setCategory(Category.COMBAT);
    addSetting(hitreg);
    addSetting(jump);
  }

  protected void onEnable() {
    EVENTS.add(UpdateListener.class, this);
  }

  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
  }

  public boolean removesMissDelay() {
    return isEnabled() && hitreg.isChecked();
  }

  public void onUpdate() {
    if (MC.player != null && jump.isChecked()) {
      var access = (LivingEntityAccessor) MC.player;
      if (access.wurst_getNoJumpDelay() > 3) access.wurst_setNoJumpDelay(3);
    }
  }
}
