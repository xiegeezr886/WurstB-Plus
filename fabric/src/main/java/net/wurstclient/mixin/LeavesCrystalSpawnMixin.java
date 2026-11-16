/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class LeavesCrystalSpawnMixin {
  @Inject(method = "addEntity", at = @At("RETURN"))
  private void leaves$spawn(int id, Entity entity, CallbackInfo ci) {
    var hax = WurstClient.INSTANCE.getHax();
    if (hax != null && entity instanceof EndCrystal crystal)
      hax.crystalAuraPlusHack.onCrystalAdded(crystal);
  }
}
