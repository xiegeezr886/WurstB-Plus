/* Replenish behavior ported from MrBZBZ/LeavesHack, commit 13daf96.
 * SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import java.util.Arrays;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.wurstclient.Category;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

/** Snapshots every hotbar slot, including unselected slots and consumed potions. */
public final class ReplenishHack extends Hack implements UpdateListener {
  private final CheckboxSetting pauseWhileUsing =
      new CheckboxSetting("Pause while using", "Pauses while you are using an item.", true);
  private final SliderSetting pauseTime =
      new SliderSetting(
          "Pause time",
          "Time after using an item before refilling resumes.",
          200,
          0,
          1000,
          10,
          ValueDisplay.INTEGER.withSuffix(" ms"));
  private final CheckboxSetting noPotion =
      new CheckboxSetting("No potion", "Do not replenish potions.", true);
  private final CheckboxSetting noTools =
      new CheckboxSetting("No tools", "Do not replace tools or weapons.", true);
  private final ItemListSetting blacklist =
      new ItemListSetting(
          "Blacklist", "Items that must not be replenished.", "minecraft:shulker_box");
  private final ItemStack[] previous = new ItemStack[9];
  private LocalPlayer owner;
  private boolean snapshot;
  private long resumeAt;

  public ReplenishHack() {
    super("Replenish");
    setCategory(Category.PLAYER);
    addSetting(pauseWhileUsing);
    addSetting(pauseTime);
    addSetting(noPotion);
    addSetting(noTools);
    addSetting(blacklist);
    Arrays.fill(previous, ItemStack.EMPTY);
  }

  @Override
  protected void onEnable() {
    reset();
    EVENTS.add(UpdateListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
    reset();
  }

  private void reset() {
    Arrays.fill(previous, ItemStack.EMPTY);
    snapshot = false;
    owner = MC.player;
    resumeAt = 0;
  }

  @Override
  public void onUpdate() {
    if (owner != MC.player) reset();
    if (MC.player == null
        || MC.level == null
        || MC.gameMode == null
        || MC.player.isCreative()
        || MC.gui.screen() != null
        || MC.player.containerMenu != MC.player.inventoryMenu
        || !MC.player.containerMenu.getCarried().isEmpty()) {
      reset();
      return;
    }
    if (!snapshot) saveSnapshot();
    if (pauseWhileUsing.isChecked() && (MC.options.keyUse.isDown() || MC.player.isUsingItem())) {
      resumeAt = System.nanoTime() + pauseTime.getValueI() * 1_000_000L;
      return;
    }
    if (System.nanoTime() < resumeAt) return;
    var inventory = MC.player.getInventory();
    for (int slot = 0; slot < 9; slot++) {
      ItemStack wanted = previous[slot];
      ItemStack current = inventory.getItem(slot);
      if (skip(wanted)) continue;
      boolean consumed = current.getItem() != wanted.getItem();
      if (!consumed) continue;
      for (int source = 9; source < 36; source++) {
        ItemStack candidate = inventory.getItem(source);
        if (candidate.isEmpty() || !ItemStack.isSameItemSameComponents(wanted, candidate)) continue;
        click(source, slot, ContainerInput.SWAP);
        break;
      }
    }
    saveSnapshot();
  }

  private boolean skip(ItemStack stack) {
    if (stack.isEmpty()
        || blacklist
            .getItemNames()
            .contains(
                net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem())
                    .toString())) return true;
    if (noPotion.isChecked() && stack.getItem() instanceof PotionItem) return true;
    return noTools.isChecked()
        && (stack.has(net.minecraft.core.component.DataComponents.TOOL)
            || stack.is(net.minecraft.tags.ItemTags.SWORDS)
            || stack.getItem() instanceof TridentItem);
  }

  private void click(int slot, int button, ContainerInput type) {
    MC.gameMode.handleContainerInput(
        MC.player.inventoryMenu.containerId, slot, button, type, MC.player);
  }

  private void saveSnapshot() {
    for (int i = 0; i < 9; i++) previous[i] = MC.player.getInventory().getItem(i).copy();
    snapshot = true;
  }
}
