/* Epsilon AutoTotem strict pickup/swap policy. GPL-3.0. */
package net.wurstclient.hacks;

import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.wurstclient.Category;
import net.wurstclient.epsilon.EpsilonTargets;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class AutoTotemHack extends Hack implements UpdateListener {
  private final CheckboxSetting strict = new CheckboxSetting("Strict", true);
  private final SliderSetting health =
      new SliderSetting("Health", 16, 0, 36, .5, ValueDisplay.DECIMAL);
  private final CheckboxSetting gapple = new CheckboxSetting("Check gapple", true);

  public AutoTotemHack() {
    super("AutoTotem");
    setCategory(Category.COMBAT);
    addSetting(strict);
    addSetting(health);
    addSetting(gapple);
  }

  protected void onEnable() {
    EVENTS.add(UpdateListener.class, this);
  }

  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
  }

  public void onUpdate() {
    if (!EpsilonTargets.ready()
        || MC.screen != null
        || MC.player.containerMenu != MC.player.inventoryMenu
        || !MC.player.inventoryMenu.getCarried().isEmpty()
        || MC.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
    var held = MC.player.getMainHandItem();
    if (MC.player.getHealth() + MC.player.getAbsorptionAmount() > health.getValue()
        && !MC.player.getOffhandItem().isEmpty()
        && MC.player.getY() >= MC.level.getMinBuildHeight()
        && !(gapple.isChecked()
            && (held.is(Items.GOLDEN_APPLE) || held.is(Items.ENCHANTED_GOLDEN_APPLE)))) return;
    int slot = -1;
    for (int i = 0; i < 36; i++)
      if (MC.player.getInventory().getItem(i).is(Items.TOTEM_OF_UNDYING)) {
        slot = i < 9 ? i + 36 : i;
        break;
      }
    if (slot < 0) return;
    if (!strict.isChecked()) {
      MC.gameMode.handleInventoryMouseClick(0, slot, 40, ClickType.SWAP, MC.player);
      return;
    }
    MC.gameMode.handleInventoryMouseClick(0, slot, 0, ClickType.PICKUP, MC.player);
    MC.gameMode.handleInventoryMouseClick(0, 45, 0, ClickType.PICKUP, MC.player);
    if (!MC.player.inventoryMenu.getCarried().isEmpty())
      MC.gameMode.handleInventoryMouseClick(0, slot, 0, ClickType.PICKUP, MC.player);
  }
}
