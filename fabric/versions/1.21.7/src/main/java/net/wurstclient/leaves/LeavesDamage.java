/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.leaves;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import net.wurstclient.util.DamageUtils;

/** Explosion exposure with a scoped block override, without mutating the world. */
public final class LeavesDamage {
  private LeavesDamage() {}

  public static float damage(Vec3 source, LivingEntity target, float power, BlockPos ignored) {
    return damage(source, target, power, ignored, Vec3.ZERO, false);
  }

  public static float crystal(Vec3 source, LivingEntity target, BlockPos base, boolean predict) {
    Vec3 movement = predict ? target.getDeltaMovement() : Vec3.ZERO;
    if (!target.level().noCollision(target, target.getBoundingBox().move(movement)))
      movement = Vec3.ZERO;
    return damage(source, target, 6, base, movement, true);
  }

  private static float damage(
      Vec3 source,
      LivingEntity target,
      float power,
      BlockPos ignored,
      Vec3 movement,
      boolean obsidian) {
    var world = WurstClient.MC.level;
    if (world == null || target == null || power <= 0) return 0;
    BlockGetter view =
        new BlockGetter() {
          public BlockEntity getBlockEntity(BlockPos pos) {
            return pos.equals(ignored) ? null : world.getBlockEntity(pos);
          }

          public BlockState getBlockState(BlockPos pos) {
            return pos.equals(ignored)
                ? (obsidian ? Blocks.OBSIDIAN : Blocks.AIR).defaultBlockState()
                : world.getBlockState(pos);
          }

          public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
          }

          public int getHeight() {
            return world.getHeight();
          }

          public int getMinY() {
            return world.getMinY();
          }
        };
    double distance = target.position().add(movement).distanceTo(source) / (power * 2);
    if (distance > 1
        || target instanceof net.minecraft.world.entity.player.Player player
            && player.getAbilities().invulnerable) return 0;
    var box = target.getBoundingBox().move(movement);
    double dx = 1 / (box.getXsize() * 2 + 1),
        dy = 1 / (box.getYsize() * 2 + 1),
        dz = 1 / (box.getZsize() * 2 + 1);
    double ox = (1 - Math.floor(1 / dx) * dx) / 2, oz = (1 - Math.floor(1 / dz) * dz) / 2;
    int visible = 0, total = 0;
    for (double x = 0; x <= 1; x += dx)
      for (double y = 0; y <= 1; y += dy)
        for (double z = 0; z <= 1; z += dz) {
          Vec3 sample =
              new Vec3(
                  box.minX + x * box.getXsize() + ox,
                  box.minY + y * box.getYsize(),
                  box.minZ + z * box.getZsize() + oz);
          if (view.clip(
                      new ClipContext(
                          sample,
                          source,
                          ClipContext.Block.COLLIDER,
                          ClipContext.Fluid.NONE,
                          target))
                  .getType()
              == HitResult.Type.MISS) visible++;
          total++;
        }

    double impact = (1 - distance) * visible / Math.max(1D, total);
    float damage =
        DamageUtils.applyDifficulty(
            (float) ((impact * impact + impact) / 2 * 7 * power * 2 + 1), world.getDifficulty());
    damage =
        CombatRules.getDamageAfterAbsorb(
            target, damage, world.damageSources().explosion(null, null),
            target.getArmorValue(),
            (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
    damage =
        CombatRules.getDamageAfterMagicAbsorb(
            damage,
            net.wurstclient.util.V152ItemCompat.explosionProtection(target));
    if (target.hasEffect(MobEffects.RESISTANCE))
      damage *=
          Math.max(0, 25 - (target.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * 5)
              / 25F;
    return Math.max(0, damage);
  }
}
