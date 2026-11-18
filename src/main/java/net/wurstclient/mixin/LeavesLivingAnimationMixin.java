/* Standing flight adapted from LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public class LeavesLivingAnimationMixin {
  @WrapOperation(
      method = "tick",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/LivingEntity;isFallFlying()Z"))
  private boolean leaves$standingAnimation(LivingEntity entity, Operation<Boolean> original) {
    var hax = WurstClient.INSTANCE.getHax();
    return entity == WurstClient.MC.player
            && hax != null
            && hax.fireworkElytraFlyHack.standingFlight()
        ? false
        : original.call(entity);
  }
}
