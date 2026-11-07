package net.fabricmc.fabric.mixin.networking;

import com.llamalad7.mixinextras.sugar.Local;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.EncoderException;
import net.minecraft.network.codec.IdDispatchCodec;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({IdDispatchCodec.class})
public abstract class PacketCodecDispatcherMixin<B extends ByteBuf, V, T> implements StreamCodec<B, V> {
   @Inject(
      method = {"encode(Lio/netty/buffer/ByteBuf;Ljava/lang/Object;)V"},
      at = {@At(
         value = "NEW",
         target = "(Ljava/lang/String;Ljava/lang/Throwable;)Lio/netty/handler/codec/EncoderException;"
      )}
   )
   public void encode(B byteBuf, V packet, CallbackInfo ci, @Local(ordinal = 1) T packetId, @Local Exception e) {
      CustomPacketPayload payload = null;
      if (packet instanceof ServerboundCustomPayloadPacket customPayloadC2SPacket) {
         payload = customPayloadC2SPacket.payload();
      } else if (packet instanceof ClientboundCustomPayloadPacket customPayloadS2CPacket) {
         payload = customPayloadS2CPacket.payload();
      }

      if (payload != null && payload.type() != null) {
         throw new EncoderException("Failed to encode packet '%s' (%s)".formatted(packetId, payload.type().id().toString()), e);
      }
   }
}
