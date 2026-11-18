package net.wurstclient.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

/** Item-component equivalents of the pre-1.20.5 combat inventory operations. */
public final class V152ItemCompat {
  private V152ItemCompat() {}

  public static EquipmentSlot armorSlot(int type) {
    return switch (type) {
      case 0 -> EquipmentSlot.FEET; case 1 -> EquipmentSlot.LEGS;
      case 2 -> EquipmentSlot.CHEST; case 3 -> EquipmentSlot.HEAD;
      default -> throw new IllegalArgumentException("Invalid armor slot: " + type);
    };
  }
  public static java.util.List<ItemStack> armorSlots(LivingEntity entity) {
    return java.util.stream.IntStream.range(0, 4).mapToObj(i -> entity.getItemBySlot(armorSlot(i))).toList();
  }
  public static boolean bindingCurse(ItemStack stack) {
    return EnchantmentUtils.getLevel(Enchantments.BINDING_CURSE, stack) > 0;
  }

  public static int armorType(ItemStack stack) {
    var equipped = stack.get(DataComponents.EQUIPPABLE);
    if (equipped == null) return -1;
    return switch (equipped.slot()) {
      case FEET -> 0;
      case LEGS -> 1;
      case CHEST -> 2;
      case HEAD -> 3;
      default -> -1;
    };
  }

  public static int armorValue(ItemStack stack) {
    double[] armor = {0}, toughness = {0};
    var equipped = stack.get(DataComponents.EQUIPPABLE);
    if (equipped == null) return 0;
    stack.forEachModifier(equipped.slot(), (attribute, modifier) -> {
      if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) return;
      if (attribute.is(Attributes.ARMOR)) armor[0] += modifier.amount();
      if (attribute.is(Attributes.ARMOR_TOUGHNESS)) toughness[0] += modifier.amount();
    });
    return (int) (armor[0] + Math.ceil(toughness[0]));
  }

  public static int armorProtection(ItemStack stack, LivingEntity player) {
    int[] result = {0};
    var equipped = stack.get(DataComponents.EQUIPPABLE);
    if (equipped == null) return 0;
    stack.forEachModifier(equipped.slot(), (attribute, modifier) -> {
      if (!attribute.is(Attributes.ARMOR) && !attribute.is(Attributes.ARMOR_TOUGHNESS)) return;
      if (modifier.operation() == AttributeModifier.Operation.ADD_VALUE)
        result[0] += (int) modifier.amount();
      else if (modifier.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
        result[0] += (int) (modifier.amount() * player.getAttributeBaseValue(attribute));
    });
    return result[0];
  }

  public static double weaponDamage(ItemStack stack) {
    double[] result = {0};
    stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
      if (attribute.is(Attributes.ATTACK_DAMAGE)
          && modifier.operation() == AttributeModifier.Operation.ADD_VALUE)
        result[0] += modifier.amount();
    });
    return result[0];
  }

  public static int explosionProtection(LivingEntity entity) {
    int result = 0;
    for (EquipmentSlot slot : EquipmentSlot.values()) {
      if (!slot.isArmor()) continue;
      ItemStack armor = entity.getItemBySlot(slot);
      result += EnchantmentUtils.getLevel(Enchantments.PROTECTION, armor)
          + EnchantmentUtils.getLevel(Enchantments.BLAST_PROTECTION, armor) * 2;
    }
    return Math.min(20, result);
  }
}
