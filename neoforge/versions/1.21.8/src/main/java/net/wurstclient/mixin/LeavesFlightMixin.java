/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public class LeavesFlightMixin {
  @Inject(
      method = "travel",
      at = {@At("HEAD"), @At("RETURN")})
  private void leaves$travel(Vec3 input, CallbackInfo ci) {
    var hax = WurstClient.INSTANCE.getHax();
    if ((Object) this == WurstClient.MC.player && hax != null) hax.fireworkElytraFlyHack.onTravel();
  }

  @WrapOperation(
      method = "getDesiredPose",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/player/Player;isFallFlying()Z"))
  private boolean leaves$pose(Player player, Operation<Boolean> original) {
    var hax = WurstClient.INSTANCE.getHax();
    if (player == WurstClient.MC.player
        && hax != null
        && hax.fireworkElytraFlyHack.standingFlight()) return false;
    return original.call(player);
  }
}
