package net.wurstclient.util;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import org.junit.jupiter.api.Test;

final class BrandPayloadCodecTest {
  @Test
  void proxyCodecsUseTheSameNettyReleaseAsTheGameCore() {
    var versions = io.netty.util.Version.identify();
    String common = versions.get("netty-common").artifactVersion();
    for (String module : new String[] {"netty-buffer", "netty-transport", "netty-codec-base",
        "netty-handler", "netty-handler-proxy", "netty-codec-socks"}) {
      assertNotNull(versions.get(module), "Missing " + module);
      assertEquals(common, versions.get(module).artifactVersion(), "Mismatched " + module);
    }
  }

  @Test
  void brandPayloadWritesExactlyOneStringAndConsumesAllBytes() {
    for (String brand : new String[] {"fabric", "vanilla", "WurstB+", "测试客户端"}) {
      FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
      try {
        BrandPayload.STREAM_CODEC.encode(buffer, new BrandPayload(brand));
        assertEquals(1 + brand.getBytes(StandardCharsets.UTF_8).length, buffer.readableBytes());
        assertEquals(brand, BrandPayload.STREAM_CODEC.decode(buffer).brand());
        assertEquals(0, buffer.readableBytes(), "Trailing brand bytes disconnect configuration");
      } finally {
        buffer.release();
      }
    }
  }
}
