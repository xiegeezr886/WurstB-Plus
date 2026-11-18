/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FireworkRocketEntity.class)
public interface LeavesFireworkAccessor {
  @Accessor("life")
  int leaves$getLife();

  @Accessor("attachedToEntity")
  LivingEntity leaves$getAttached();
}
