/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.client.renderer.GameRenderer;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public class LeavesFovMixin {
  @WrapOperation(
      method = "getFov",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F"))
  private float leaves$fov(float delta, float a, float b, Operation<Float> original) {
    var hax = WurstClient.INSTANCE.getHax();
    if (WurstClient.MC.player != null && hax != null && !hax.globalSettingHack.changeFov())
      return 1;
    return original.call(delta, a, b);
  }
}
