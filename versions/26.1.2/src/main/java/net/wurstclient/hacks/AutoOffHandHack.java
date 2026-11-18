/* AutoOffHand ported from MrBZBZ/LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.*;
import net.wurstclient.Category;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.leaves.LeavesUtils;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

public final class AutoOffHandHack extends Hack implements UpdateListener {
  private final CheckboxSetting useGap = new CheckboxSetting("UseGap", true);
  private final EnumSetting<GapMode> gapMode =
      new EnumSetting<>("GapMode", GapMode.values(), GapMode.MainHand);
  private final CheckboxSetting pickaxe = new CheckboxSetting("WhenPickAxe", true);
  private final CheckboxSetting sword = new CheckboxSetting("WhenSword", true);
  private final CheckboxSetting totem = new CheckboxSetting("WhenTotem", true);
  private final CheckboxSetting autoMain = new CheckboxSetting("AutoMain", true);
  private final SliderSetting mainSlot =
      new SliderSetting("MainSlot", 0, 0, 8, 1, ValueDisplay.INTEGER);
  private final SliderSetting health =
      new SliderSetting("CheckHealth", 10, 0, 36, 1, ValueDisplay.INTEGER);
  private int oldSlot = -1, swapSlot = -1;
  private LocalPlayer owner;
  private ItemStack original = ItemStack.EMPTY;
  private GapMode activeMode;
  private int activeSlot = -1;

  public AutoOffHandHack() {
    super("AutoOffHand");
    setCategory(Category.PLAYER);
    for (Setting s :
        new Setting[] {useGap, gapMode, pickaxe, sword, totem, autoMain, mainSlot, health})
      addSetting(s);
  }

  @Override
  protected void onEnable() {
    if (owner != MC.player) {
      oldSlot = swapSlot = -1;
    }
    owner = MC.player;
    EVENTS.add(UpdateListener.class, this);
  }

  @Override
  protected void onDisable() {
    if (restore()) EVENTS.remove(UpdateListener.class, this);
  }

  private boolean restore() {
    if (oldSlot < 0) return true;
    if (owner != MC.player || MC.player == null) {
      oldSlot = swapSlot = -1;
      return true;
    }
    if (MC.gameMode == null
        || MC.player.containerMenu != MC.player.inventoryMenu
        || !MC.player.inventoryMenu.getCarried().isEmpty()) return false;
    var inv = MC.player.getInventory();
    if (swapSlot >= 0
        && ItemStack.isSameItemSameComponents(inv.getItem(swapSlot), original)
        && (isGap(inv.getItem(oldSlot)) || inv.getItem(oldSlot).isEmpty())) swap(swapSlot, oldSlot);
    if (inv.getSelectedSlot() == activeSlot) select(oldSlot);
    oldSlot = swapSlot = activeSlot = -1;
    original = ItemStack.EMPTY;
    return true;
  }

  @Override
  public void onUpdate() {
    if (owner != MC.player) {
      oldSlot = swapSlot = -1;
      owner = MC.player;
    }
    if (!isEnabled()) {
      if (restore()) EVENTS.remove(UpdateListener.class, this);
      return;
    }
    if (!LeavesUtils.ready()) return;
    boolean pressed = MC.options.keyUse.isDown();
    if (oldSlot >= 0 && (!pressed || !useGap.isChecked() || gapMode.getSelected() != activeMode))
      restore();
    int gap = InventoryUtils.indexOf(Items.ENCHANTED_GOLDEN_APPLE, 36);
    if (gap < 0) gap = InventoryUtils.indexOf(Items.GOLDEN_APPLE, 36);
    boolean trigger = useGap.isChecked() && pressed && (checkItem() || oldSlot >= 0);
    if (gapMode.getSelected() == GapMode.MainHand) {
      ensureTotem(40);
      if (trigger && oldSlot < 0 && gap >= 0 && !isGap(MC.player.getMainHandItem())) {
        oldSlot = MC.player.getInventory().getSelectedSlot();
        swapSlot = gap;
        activeSlot = oldSlot;
        activeMode = gapMode.getSelected();
        original = MC.player.getMainHandItem().copy();
        swap(gap, oldSlot);
        select(oldSlot);
      }
    } else if (trigger) {
      if (!isGap(MC.player.getOffhandItem()) && gap >= 0) swap(gap, 40);
      if (autoMain.isChecked()
          && MC.player.getHealth() + MC.player.getAbsorptionAmount() <= health.getValue()) {
        ensureTotem(mainSlot.getValueI());
        if (MC.player.getInventory().getItem(mainSlot.getValueI()).is(Items.TOTEM_OF_UNDYING)) {
          if (oldSlot < 0) {
            oldSlot = MC.player.getInventory().getSelectedSlot();
            activeMode = gapMode.getSelected();
          }
          activeSlot = mainSlot.getValueI();
          select(mainSlot.getValueI());
        }
      }
    } else ensureTotem(40);
  }

  private boolean checkItem() {
    Item item = MC.player.getMainHandItem().getItem();
    return pickaxe.isChecked() && item.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.PICKAXES)
        || sword.isChecked() && item.builtInRegistryHolder().is(net.minecraft.tags.ItemTags.SWORDS)
        || totem.isChecked() && item == Items.TOTEM_OF_UNDYING;
  }

  private static boolean isGap(ItemStack stack) {
    return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
  }

  private void ensureTotem(int slot) {
    if (MC.player.getInventory().getItem(slot).is(Items.TOTEM_OF_UNDYING)) return;
    int found = InventoryUtils.indexOf(Items.TOTEM_OF_UNDYING, 36);
    if (found >= 0) swap(found, slot);
  }

  private void swap(int from, int to) {
    MC.gameMode.handleContainerInput(
        MC.player.inventoryMenu.containerId,
        InventoryUtils.toNetworkSlot(from),
        to,
        net.minecraft.world.inventory.ContainerInput.SWAP,
        MC.player);
  }

  private void select(int slot) {
    MC.player.getInventory().setSelectedSlot(slot);
    net.wurstclient.leaves.LeavesSlots.INSTANCE.select(slot);
  }

  public enum GapMode {
    OffHand,
    MainHand
  }
}
