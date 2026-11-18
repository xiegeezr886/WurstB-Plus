/* AutoArmorPlus ported from MrBZBZ/LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.hacks;

import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.wurstclient.Category;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;
import net.wurstclient.util.InventoryUtils;

public final class AutoArmorPlusHack extends Hack implements UpdateListener {
  private final SliderSetting delay =
      new SliderSetting("Delay", 10, 0, 1000, 1, ValueDisplay.INTEGER.withSuffix(" ms"));
  private final CheckboxSetting autoElytra = new CheckboxSetting("AutoElytra", true);
  private final CheckboxSetting ignoreBinding =
      new CheckboxSetting(
          "IgnoreBinding", "Exclude armor with Curse of Binding from selection.", false);
  private final CheckboxSetting snowBug = new CheckboxSetting("SnowBug", false);
  private long next;

  public AutoArmorPlusHack() {
    super("AutoArmorPlus");
    setCategory(Category.COMBAT);
    for (Setting s : new Setting[] {delay, autoElytra, ignoreBinding, snowBug}) addSetting(s);
  }

  @Override
  protected void onEnable() {
    next = 0;
    EVENTS.add(UpdateListener.class, this);
  }

  @Override
  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
  }

  @Override
  public void onUpdate() {
    if (MC.player == null
        || MC.gameMode == null
        || MC.player.containerMenu != MC.player.inventoryMenu
        || !MC.player.inventoryMenu.getCarried().isEmpty()) return;
    if (MC.gui.screen() != null
        && !(MC.gui.screen() instanceof ChatScreen)
        && !(MC.gui.screen() instanceof InventoryScreen)) return;
    if (System.nanoTime() < next) return;
    next = System.nanoTime() + delay.getValueI() * 1_000_000L;
    var inv = MC.player.getInventory();
    for (int type = 0; type < 4; type++) {
      ItemStack equipped = inv.getItem(36 + type);
      if (!MC.player.isCreative() && net.wurstclient.util.V152ItemCompat.bindingCurse(equipped)) continue;
      boolean flight =
          type == 2
              && autoElytra.isChecked()
              && WURST.getHax().fireworkElytraFlyHack.requiresEquippedElytra();
      boolean snow = type == 0 && snowBug.isChecked() && MC.player.hurtTime > 1;
      if (flight && usableElytra(equipped) || snow && equipped.is(Items.LEATHER_BOOTS)) continue;
      int best = -1, score = protection(equipped);
      for (int i = 0; i < 36; i++) {
        ItemStack candidate = inv.getItem(i);
        if (flight) {
          if (usableElytra(candidate)) {
            best = i;
            break;
          }
          continue;
        }
        if (snow && candidate.is(Items.LEATHER_BOOTS)) {
          best = i;
          break;
        }
        if (net.wurstclient.util.V152ItemCompat.armorType(candidate) < 0
            || net.wurstclient.util.V152ItemCompat.armorType(candidate) != type) continue;
        int value = protection(candidate);
        if (value > 0 && value > score) {
          best = i;
          score = value;
        }
      }
      if (best < 0) continue;
      int source = InventoryUtils.toNetworkSlot(best), target = 8 - type;
      click(source);
      click(target);
      if (!MC.player.inventoryMenu.getCarried().isEmpty()) click(source);
      MC.player.connection.send(
          new ServerboundContainerClosePacket(MC.player.inventoryMenu.containerId));
      return;
    }
  }

  private static boolean usableElytra(ItemStack stack) {
    return stack.is(Items.ELYTRA) && stack.getDamageValue() < stack.getMaxDamage() - 1;
  }

  private int protection(ItemStack stack) {
    if (stack.isEmpty()) return -1;
    if (ignoreBinding.isChecked() && net.wurstclient.util.V152ItemCompat.bindingCurse(stack)) return -1;
    if (stack.is(Items.ELYTRA)) return usableElytra(stack) ? 1 : 0;
    if (net.wurstclient.util.V152ItemCompat.armorType(stack) < 0) return 0;
    int result =
        net.wurstclient.util.EnchantmentUtils.getLevel(Enchantments.PROTECTION, stack);
    result += net.wurstclient.util.V152ItemCompat.armorProtection(stack, MC.player);
    return result;
  }

  private void click(int slot) {
    MC.gameMode.handleContainerInput(
        MC.player.inventoryMenu.containerId, slot, 0, ContainerInput.PICKUP, MC.player);
  }
}
