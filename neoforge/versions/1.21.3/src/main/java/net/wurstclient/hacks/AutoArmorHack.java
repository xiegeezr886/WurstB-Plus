/* Adapted from Epsilon AutoArmor.kt (GPL-3.0), with cursor-safe 1.20.1 swaps. */
package net.wurstclient.hacks;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.wurstclient.Category;
import net.wurstclient.epsilon.EpsilonTargets;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class AutoArmorHack extends Hack implements UpdateListener {
  private enum EnchantPriority {
    PROTECTION,
    BLAST
  }

  private enum ElytraPriority {
    NONE,
    ALWAYS,
    KEEP_EQUIPPED
  }

  private final EnumSetting<EnchantPriority> head = priority("Head"),
      body = priority("Body"),
      legs = priority("Legs"),
      feet = priority("Feet");
  private final EnumSetting<ElytraPriority> elytra =
      new EnumSetting<>("Elytra priority", ElytraPriority.values(), ElytraPriority.KEEP_EQUIPPED);
  private final CheckboxSetting noMove = new CheckboxSetting("No move", true);
  private final CheckboxSetting strict = new CheckboxSetting("Strict", false);
  private final CheckboxSetting ignoreCurse = new CheckboxSetting("Ignore binding curse", true);
  private final SliderSetting delay = new SliderSetting("Delay", 5, 0, 20, 1, ValueDisplay.INTEGER);
  private int timer;
  private Object player, world;

  private static EnumSetting<EnchantPriority> priority(String name) {
    return new EnumSetting<>(name, EnchantPriority.values(), EnchantPriority.PROTECTION);
  }

  public AutoArmorHack() {
    super("AutoArmor");
    setCategory(Category.COMBAT);
    addSetting(head);
    addSetting(body);
    addSetting(legs);
    addSetting(feet);
    addSetting(elytra);
    addSetting(noMove);
    addSetting(strict);
    addSetting(ignoreCurse);
    addSetting(delay);
  }

  protected void onEnable() {
    timer = 0;
    EVENTS.add(UpdateListener.class, this);
  }

  protected void onDisable() {
    EVENTS.remove(UpdateListener.class, this);
  }

  public void onUpdate() {
    if (player != MC.player || world != MC.level) {
      timer = 0;
      player = MC.player;
      world = MC.level;
    }
    if (!EpsilonTargets.ready()
        || MC.screen != null && !(MC.screen instanceof InventoryScreen)
        || MC.player.containerMenu != MC.player.inventoryMenu
        || !MC.player.inventoryMenu.getCarried().isEmpty()) return;
    if (noMove.isChecked()
        && (MC.player.input.forwardImpulse != 0 || MC.player.input.leftImpulse != 0)) return;
    if (timer > 0) {
      timer--;
      return;
    }
    var inv = MC.player.getInventory();
    for (int type = 0; type < 4; type++) {
      ItemStack equipped = inv.getArmor(type);
      if (!MC.player.isCreative() && net.wurstclient.util.V152ItemCompat.bindingCurse(equipped)) continue;
      if (type == 2
          && equipped.is(Items.ELYTRA)
          && elytra.getSelected() == ElytraPriority.KEEP_EQUIPPED) continue;
      int best = -1, value = score(equipped, type);
      for (int i = 0; i < 36; i++) {
        ItemStack stack = inv.getItem(i);
        if (type(stack) != type) continue;
        int next = score(stack, type);
        if (next > value) {
          value = next;
          best = i;
        }
      }
      if (best < 0) continue;
      if (strict.isChecked()) MC.player.setSprinting(false);
      int source = best < 9 ? best + 36 : best;
      MC.gameMode.handleInventoryMouseClick(0, source, 0, ClickType.PICKUP, MC.player);
      MC.gameMode.handleInventoryMouseClick(0, 8 - type, 0, ClickType.PICKUP, MC.player);
      if (!MC.player.inventoryMenu.getCarried().isEmpty())
        MC.gameMode.handleInventoryMouseClick(0, source, 0, ClickType.PICKUP, MC.player);
      timer = delay.getValueI();
      return;
    }
  }

  private static int type(ItemStack stack) {
    if (stack.is(Items.ELYTRA)) return 2;
    return net.wurstclient.util.V152ItemCompat.armorType(stack);
  }

  private int score(ItemStack stack, int type) {
    if (stack.isEmpty()) return -1;
    if (ignoreCurse.isChecked() && net.wurstclient.util.V152ItemCompat.bindingCurse(stack)) return -999;
    int value = 0;
    if (stack.is(Items.ELYTRA)) {
      if (!(stack.getDamageValue() < stack.getMaxDamage() - 1)) return 0;
      if (elytra.getSelected() == ElytraPriority.ALWAYS) value = 999;
    } else if (stack.getItem() instanceof ArmorItem item)
      value = net.wurstclient.util.V152ItemCompat.armorValue(stack) * 10;
    else return 0;
    EnchantPriority choice =
        switch (type) {
          case 0 -> feet.getSelected();
          case 1 -> legs.getSelected();
          case 2 -> body.getSelected();
          default -> head.getSelected();
        };
    return value
        + net.wurstclient.util.EnchantmentUtils.getLevel(Enchantments.PROTECTION, stack)
            * (choice == EnchantPriority.PROTECTION ? 2 : 1)
        + net.wurstclient.util.EnchantmentUtils.getLevel(Enchantments.BLAST_PROTECTION, stack)
            * (choice == EnchantPriority.BLAST ? 2 : 1);
  }
}
