/* Standing flight adapted from LeavesHack 13daf96. SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class LeavesPlayerRendererMixin {
  @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V", at = @At("RETURN"))
  private void leaves$standingFlight(AbstractClientPlayer player, PlayerRenderState state,
      float delta, CallbackInfo ci) {
    var hax = WurstClient.INSTANCE.getHax();
    if (player == WurstClient.MC.player && hax != null
        && hax.fireworkElytraFlyHack.standingFlight()) state.isFallFlying = false;
  }
}
