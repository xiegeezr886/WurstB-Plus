/* SPDX-License-Identifier: AGPL-3.0-or-later */
package net.wurstclient.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.network.PacketDecoder;
import net.wurstclient.WurstClient;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PacketDecoder.class)
public class LeavesPacketDecoderMixin {
  @WrapMethod(method = "decode")
  private void leaves$decode(
      ChannelHandlerContext context,
      ByteBuf buffer,
      List<Object> packets,
      Operation<Void> original) {
    var hax = WurstClient.INSTANCE.getHax();
    if (hax == null || !hax.globalSettingHack.packetKickFix() || WurstClient.MC.level == null) {
      original.call(context, buffer, packets);
      return;
    }
    int size = packets.size();
    try {
      original.call(context, buffer, packets);
    } catch (Exception error) {
      while (packets.size() > size) packets.remove(packets.size() - 1);
      buffer.skipBytes(buffer.readableBytes());
    }
  }
}
