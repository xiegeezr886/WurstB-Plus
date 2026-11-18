/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FireworkRocketEntity.class)
public class LeavesFireworkMixin {
  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/world/entity/LivingEntity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"))
  private Vec3 leaves$direction(LivingEntity entity, Operation<Vec3> original) {
    var hax = WurstClient.INSTANCE.getHax();
    if (entity == WurstClient.MC.player
        && hax != null
        && hax.fireworkElytraFlyHack.controlsFlight())
      return hax.fireworkElytraFlyHack.flightDirection();
    return original.call(entity);
  }
}
