/* Adapted from Epsilon AutoWeapon.kt (GPL-3.0), Fabric 1.20.1. */
package net.wurstclient.hacks;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.wurstclient.Category;
import net.wurstclient.epsilon.EpsilonTargets;
import net.wurstclient.events.PlayerAttacksEntityListener;
import net.wurstclient.events.UpdateListener;
import net.wurstclient.hack.Hack;
import net.wurstclient.settings.*;
import net.wurstclient.settings.SliderSetting.ValueDisplay;

public final class AutoWeaponHack extends Hack
    implements PlayerAttacksEntityListener, UpdateListener {
  private enum Mode {
    SIMPLE,
    SMART
  }

  private final EnumSetting<Mode> mode = new EnumSetting<>("Mode", Mode.values(), Mode.SMART);
  private final SliderSetting slot =
      new SliderSetting("Target slot", 1, 1, 9, 1, ValueDisplay.INTEGER);
  private final CheckboxSetting pauseEat = new CheckboxSetting("Pause on eat", true);
  private final CheckboxSetting onlyWeapon = new CheckboxSetting("Only on weapon", false);
  private final CheckboxSetting swapBack = new CheckboxSetting("Swap back", true);
  private final SliderSetting delay =
      new SliderSetting("Swap back delay", 200, 0, 500, 10, ValueDisplay.INTEGER);
  private final CheckboxSetting shield = new CheckboxSetting("Smart shield breaker", true);
  private final CheckboxSetting durability = new CheckboxSetting("Smart durability saver", false);
  private int original = -1, owned = -1;
  private Object player, world;
  private long restoreAt;

  public AutoWeaponHack() {
    super("AutoWeapon");
    setCategory(Category.COMBAT);
    addSetting(mode);
    addSetting(slot);
    addSetting(pauseEat);
    addSetting(onlyWeapon);
    addSetting(swapBack);
    addSetting(delay);
    addSetting(shield);
    addSetting(durability);
  }

  protected void onEnable() {
    original = owned = -1;
    EVENTS.add(PlayerAttacksEntityListener.class, this);
    EVENTS.add(UpdateListener.class, this);
  }

  protected void onDisable() {
    EVENTS.remove(PlayerAttacksEntityListener.class, this);
    EVENTS.remove(UpdateListener.class, this);
    restore();
  }

  private void restore() {
    if (player == MC.player
        && world == MC.level
        && MC.player != null
        && original >= 0
        && MC.player.getInventory().selected == owned) MC.player.getInventory().selected = original;
    original = owned = -1;
  }

  public void onUpdate() {
    if (player != MC.player || world != MC.level) {
      original = owned = -1;
      player = MC.player;
      world = MC.level;
    }
    if (original >= 0
        && (System.nanoTime() >= restoreAt || MC.screen != null || !EpsilonTargets.ready()))
      restore();
    if (original >= 0 && MC.player.getInventory().selected != owned) original = owned = -1;
  }

  public void onPlayerAttacksEntity(Entity entity) {
    onUpdate();
    if (!EpsilonTargets.ready()
        || !(entity instanceof LivingEntity target)
        || original >= 0
        || pauseEat.isChecked() && MC.player.isUsingItem()
        || onlyWeapon.isChecked() && !EpsilonTargets.weapon()
        || WURST.getHax().scaffoldWalkHack.isEnabled()) return;
    int selected = mode.getSelected() == Mode.SIMPLE ? slot.getValueI() - 1 : bestSlot(target);
    if (selected < 0 || selected == MC.player.getInventory().selected) return;
    player = MC.player;
    world = MC.level;
    if (swapBack.isChecked()) {
      original = MC.player.getInventory().selected;
      owned = selected;
      restoreAt = System.nanoTime() + delay.getValueI() * 1_000_000L;
    }
    MC.player.getInventory().selected = selected;
    // The attack event runs before vanilla synchronizes the selected hotbar slot.
  }

  private int bestSlot(LivingEntity target) {
    var inv = MC.player.getInventory();
    if (shield.isChecked() && target.isBlocking()) {
      if (inv.getSelected().is(ItemTags.AXES)) return -1;
      for (int i = 0; i < 9; i++) if (inv.getItem(i).is(ItemTags.AXES)) return i;
    }
    int best = -1;
    double value = score(inv.getSelected(), target);
    for (int i = 0; i < 9; i++) {
      double candidate = score(inv.getItem(i), target);
      if (candidate > value) {
        value = candidate;
        best = i;
      }
    }
    return best;
  }

  private double score(ItemStack stack, LivingEntity target) {
    double score =
        durability.isChecked()
            ? (!stack.isDamageableItem() ? 4 : level(stack, Enchantments.UNBREAKING) * .05)
            : 0;
    if (stack.isEmpty()) return score;
    boolean protectedFire = target.hasEffect(MobEffects.FIRE_RESISTANCE);
    for (ItemStack armor : target.getArmorSlots())
      protectedFire |= level(armor, Enchantments.FIRE_PROTECTION) > 0;
    if (!target.isOnFire() && !protectedFire && level(stack, Enchantments.FIRE_ASPECT) > 0)
      score += 30;
    if (!(target instanceof Player))
      score +=
          level(stack, Enchantments.MOB_LOOTING)
              * (target.getHealth() < 20 || target.isOnFire() ? 10 : 5);
    int sharp = level(stack, Enchantments.SHARPNESS);
    double damage = sharp > 0 ? (1 + .5 * (sharp - 1)) * 3 : 0;
    if (target.getMobType() == MobType.UNDEAD) damage += level(stack, Enchantments.SMITE) * 5;
    if (target.getMobType() == MobType.ARTHROPOD)
      damage += level(stack, Enchantments.BANE_OF_ARTHROPODS) * 5;
    score += damage * (target.isOnFire() ? 1.5 : 1) + level(stack, Enchantments.SWEEPING_EDGE) * 3;
    if (target.getMobType() == MobType.WATER) score += level(stack, Enchantments.IMPALING) * 5;
    return score;
  }

  private static int level(ItemStack stack, Enchantment enchantment) {
    return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
  }
}
