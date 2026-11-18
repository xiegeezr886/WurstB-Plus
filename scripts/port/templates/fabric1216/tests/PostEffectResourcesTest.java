package net.wurstclient.util.render;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.UniformValue;
import org.junit.jupiter.api.Test;

class PostEffectResourcesTest {
  private static final Path ASSETS = Path.of("src/main/resources/assets");

  @Test void effectUniformBlocksMatchTheRuntimeCodec() throws Exception {
    for (var effect : PostEffectQueue.Effect.values()) {
      var json = JsonParser.parseString(Files.readString(ASSETS.resolve(
          "wurst/post_effect/target_" + effect.toString().toLowerCase() + ".json")));
      var config = PostChainConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
      assertEquals(2, config.passes().size());
      var block = config.passes().getFirst().uniforms().get("WurstEffect");
      assertEquals(effect.ordinal(), ((UniformValue.IntUniform)block.get(0)).value());
      assertInstanceOf(UniformValue.FloatUniform.class, block.get(1));
      var blit = config.passes().getLast().uniforms().get("BlitConfig");
      assertEquals(new org.joml.Vector4f(1,1,1,1), ((UniformValue.Vec4Uniform)blit.getFirst()).value(),
          "A zero composite tint makes every target effect invisible");
    }
    var blur = PostChainConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
        Files.readString(ASSETS.resolve("wurstpenguin/post_effect/opai_background.json")))).getOrThrow();
    assertEquals(2, blur.passes().size());
    for (var pass : blur.passes()) {
      var block = pass.uniforms().get("BlurConfig");
      assertInstanceOf(UniformValue.Vec2Uniform.class, block.get(0));
      assertTrue(((UniformValue.FloatUniform)block.get(1)).value() > 0);
    }
  }

  @Test void fragmentDeclaresTheSamplerAndStd140Blocks() throws Exception {
    String shader = Files.readString(ASSETS.resolve("wurst/shaders/program/target_effect.fsh"));
    for (String declaration : new String[]{"sampler2D InSampler", "uniform SamplerInfo", "uniform WurstEffect", "int Mode; float Time;"})
      assertTrue(shader.contains(declaration), declaration);
  }
}
