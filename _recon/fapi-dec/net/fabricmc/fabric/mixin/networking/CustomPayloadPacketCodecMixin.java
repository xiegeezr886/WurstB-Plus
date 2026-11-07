package net.fabricmc.fabric.mixin.networking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.impl.networking.CustomPayloadTypeProvider;
import net.fabricmc.fabric.impl.networking.FabricCustomPayloadPacketCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.TypeAndCodec;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(
   targets = {"net/minecraft/network/packet/CustomPayload$1"}
)
public abstract class CustomPayloadPacketCodecMixin<B extends FriendlyByteBuf>
   implements StreamCodec<B, CustomPacketPayload>,
   FabricCustomPayloadPacketCodec<B> {
   @Unique
   private CustomPayloadTypeProvider<B> customPayloadTypeProvider;

   public void fabric_setPacketCodecProvider(CustomPayloadTypeProvider<B> customPayloadTypeProvider) {
      if (this.customPayloadTypeProvider != null) {
         throw new IllegalStateException("Payload codec provider is already set!");
      } else {
         this.customPayloadTypeProvider = customPayloadTypeProvider;
      }
   }

   @WrapOperation(
      method = {"encode(Lnet/minecraft/network/PacketByteBuf;Lnet/minecraft/network/packet/CustomPayload$Id;Lnet/minecraft/network/packet/CustomPayload;)V", "decode(Lnet/minecraft/network/PacketByteBuf;)Lnet/minecraft/network/packet/CustomPayload;"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/packet/CustomPayload$1;getCodec(Lnet/minecraft/util/Identifier;)Lnet/minecraft/network/codec/PacketCodec;"
      )}
   )
   private StreamCodec<B, ? extends CustomPacketPayload> wrapGetCodec(
      @Coerce StreamCodec<B, CustomPacketPayload> instance,
      ResourceLocation identifier,
      Operation<StreamCodec<B, CustomPacketPayload>> original,
      B packetByteBuf
   ) {
      if (this.customPayloadTypeProvider != null) {
         TypeAndCodec<B, ? extends CustomPacketPayload> payloadType = this.customPayloadTypeProvider.get(packetByteBuf, identifier);
         if (payloadType != null) {
            return payloadType.codec();
         }
      }

      return (StreamCodec<B, ? extends CustomPacketPayload>)original.call(new Object[]{instance, identifier});
   }
}
