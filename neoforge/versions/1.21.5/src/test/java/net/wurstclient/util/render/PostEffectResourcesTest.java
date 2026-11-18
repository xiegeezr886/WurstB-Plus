package net.wurstclient.util.render;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.renderer.PostChainConfig;
import org.junit.jupiter.api.Test;

class PostEffectResourcesTest {
  private static final Path ASSETS = Path.of("src/main/resources/assets");

  @Test
  void everyEffectUsesTheRuntimePostChainCodec() throws Exception {
    for (var effect : PostEffectQueue.Effect.values()) {
      var json=JsonParser.parseString(Files.readString(ASSETS.resolve(
          "wurst/post_effect/target_"+effect.toString().toLowerCase()+".json")));
      var config=PostChainConfig.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
      assertEquals(2, config.passes().size());
      var pass=config.passes().getFirst();
      assertEquals("wurst:program/target_effect", pass.fragmentShaderId().toString());
      assertTrue(pass.uniforms().stream().anyMatch(u -> u.name().equals("Mode") && u.type().equals("int")));
      assertTrue(pass.uniforms().stream().anyMatch(u -> u.name().equals("Time") && u.type().equals("float")));
      var composite = config.passes().getLast();
      var tint = composite.uniforms().stream().filter(u -> u.name().equals("ColorModulate")).findFirst().orElseThrow();
      assertEquals("vec4", tint.type());
      assertEquals(java.util.List.of(1F, 1F, 1F, 1F), tint.values().orElseThrow(),
          "The vanilla blit fragment multiplies by this tint; zero makes the effect invisible");
    }
    var blur=PostChainConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
        Files.readString(ASSETS.resolve("wurstpenguin/post_effect/opai_background.json")))).getOrThrow();
    assertEquals(2, blur.passes().size());
    for (var pass : blur.passes()) {
      assertEquals("minecraft:post/blur", pass.vertexShaderId().toString());
      assertTrue(pass.uniforms().stream().anyMatch(u -> u.name().equals("BlurDir") && u.type().equals("vec2")));
    }
  }

  @Test
  void targetFragmentUsesTheBoundSamplerAndUniforms() throws Exception {
    String shader=Files.readString(ASSETS.resolve("wurst/shaders/program/target_effect.fsh"));
    for (String declaration : new String[]{"sampler2D InSampler", "vec2 InSize", "float Time", "int Mode"})
      assertTrue(shader.contains(declaration), declaration);
  }
}
