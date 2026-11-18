/* Standing flight adapted from LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PlayerRenderer.class)
public class LeavesPlayerRendererMixin {
  @WrapOperation(
      method =
          "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/player/AbstractClientPlayer;isFallFlying()Z"))
  private boolean leaves$standingFlight(AbstractClientPlayer player, Operation<Boolean> original) {
    var hax = WurstClient.INSTANCE.getHax();
    return player == WurstClient.MC.player
            && hax != null
            && hax.fireworkElytraFlyHack.standingFlight()
        ? false
        : original.call(player);
  }
}
