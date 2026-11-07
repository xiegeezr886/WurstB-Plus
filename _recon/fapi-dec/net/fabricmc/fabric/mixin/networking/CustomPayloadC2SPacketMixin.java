package net.fabricmc.fabric.mixin.networking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.fabricmc.fabric.impl.networking.FabricCustomPayloadPacketCodec;
import net.fabricmc.fabric.impl.networking.PayloadTypeRegistryImpl;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.FallbackProvider;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.TypeAndCodec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({ServerboundCustomPayloadPacket.class})
public class CustomPayloadC2SPacketMixin {
   @WrapOperation(
      method = {"<clinit>"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/packet/CustomPayload;createCodec(Lnet/minecraft/network/packet/CustomPayload$CodecFactory;Ljava/util/List;)Lnet/minecraft/network/codec/PacketCodec;"
      )}
   )
   private static StreamCodec<FriendlyByteBuf, CustomPacketPayload> wrapCodec(
      FallbackProvider<FriendlyByteBuf> unknownCodecFactory,
      List<TypeAndCodec<FriendlyByteBuf, ?>> types,
      Operation<StreamCodec<FriendlyByteBuf, CustomPacketPayload>> original
   ) {
      StreamCodec<FriendlyByteBuf, CustomPacketPayload> codec = (StreamCodec<FriendlyByteBuf, CustomPacketPayload>)original.call(
         new Object[]{unknownCodecFactory, types}
      );
      FabricCustomPayloadPacketCodec<FriendlyByteBuf> fabricCodec = (FabricCustomPayloadPacketCodec<FriendlyByteBuf>)codec;
      fabricCodec.fabric_setPacketCodecProvider(
         (packetByteBuf, identifier) -> packetByteBuf instanceof RegistryFriendlyByteBuf
            ? PayloadTypeRegistryImpl.PLAY_C2S.get(identifier)
            : PayloadTypeRegistryImpl.CONFIGURATION_C2S.get(identifier)
      );
      return codec;
   }
}
