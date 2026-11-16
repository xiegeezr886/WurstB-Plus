package net.wurstclient.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.wurstclient.seed.SeedCrackerBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class SeedSessionMixin {
  @Inject(method = "handleLogin", at = @At("TAIL"))
  private void seedSession(ClientboundLoginPacket packet, CallbackInfo ci) {
    SeedCrackerBridge.SESSION.begin(packet.seed());
  }

  @Inject(method = "handleRespawn", at = @At("TAIL"))
  private void seedDimension(ClientboundRespawnPacket packet, CallbackInfo ci) {
    SeedCrackerBridge.SESSION.changeDimension(packet.getSeed());
  }
}
