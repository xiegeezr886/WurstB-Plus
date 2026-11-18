/* NeoRaven M3LoadingIndicator adaptation; GPL-3.0. See LOADING-NOTICE.txt. */
package net.wurstclient.neoraven.loading;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.wurstclient.hud2.nanovg.GlStateGuard;
import net.wurstclient.neoraven.music.Ease;
import org.lwjgl.opengl.*;

public final class NeoRavenLoadingShape {
  private static final long START = System.nanoTime();
  private static final float[][] FORMS = {
    {12, .72F, .28F, 0}, {9, .68F, .32F, 0}, {5, .70F, .30F, 0}, {9, .68F, .32F, 1}
  };
  private static int program, vao;
  private static boolean failed;
  private static final Map<String, Integer> UNIFORMS = new HashMap<>();
  private static final String VERTEX =
      """
      #version 150
      uniform vec2 screenSize;
      uniform vec2 center;
      uniform float halfSize;
      out vec2 localPos;
      void main() {
        vec2 uv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
        localPos = (uv * 2.0 - 1.0) * halfSize;
        vec2 position = center + localPos;
        gl_Position = vec4(position.x / screenSize.x * 2.0 - 1.0,
            1.0 - position.y / screenSize.y * 2.0, 0.0, 1.0);
      }
      """;

  private NeoRavenLoadingShape() {}

  public static boolean ready() {
    return program != 0 && !failed;
  }

  public static boolean draw(float cx, float cy, float size, int color) {
    if (failed) return false;
    try (GlStateGuard guard = GlStateGuard.capture()) {
      if (program == 0) create();
      var mc = Minecraft.getInstance();
      var target = mc.getMainRenderTarget();
      target.bindWrite(false);
      GL11.glViewport(0, 0, target.width, target.height);
      GL20.glUseProgram(program);
      GL30.glBindVertexArray(vao);
      GL11.glDisable(GL11.GL_DEPTH_TEST);
      GL11.glDepthMask(false);
      GL11.glDisable(GL11.GL_CULL_FACE);
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      GL11.glDisable(GL11.GL_STENCIL_TEST);
      GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
      GL11.glColorMask(true, true, true, true);
      GL11.glEnable(GL11.GL_BLEND);
      GL14.glBlendFuncSeparate(
          GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
      GL20.glUniform2f(
          uniform("screenSize"),
          mc.getWindow().getGuiScaledWidth(),
          mc.getWindow().getGuiScaledHeight());
      GL20.glUniform2f(uniform("center"), cx, cy);
      scalar("halfSize", size * .7F);
      float ms = (System.nanoTime() - START) / 1e6F, cycles = ms / 900;
      int index = (int) Math.floor(cycles);
      float eased = Ease.EMPHASIZED.get(cycles - index), radius = size / 2;
      float[] a = FORMS[index % 4], b = FORMS[(index + 1) % 4];
      scalar("uPixel", (float) (1 / mc.getWindow().getGuiScale()));
      scalar("uSidesA", a[0]);
      scalar("uApothemA", a[1] * radius);
      scalar("uRoundA", a[2] * radius);
      scalar("uSidesB", b[0]);
      scalar("uApothemB", b[1] * radius);
      scalar("uRoundB", b[2] * radius);
      scalar("uMorph", eased);
      scalar("uCircle", a[3] + (b[3] - a[3]) * eased);
      scalar("uRadius", radius);
      scalar("uRotation", ms / 1800 * (float) (Math.PI * 2));
      scalar("uScale", 1 - .05F * (1 - eased));
      GL20.glUniform4f(
          uniform("vertexColor"),
          (color >> 16 & 255) / 255F,
          (color >> 8 & 255) / 255F,
          (color & 255) / 255F,
          (color >>> 24) / 255F);
      GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
      return true;
    } catch (Exception e) {
      failed = true;
      close();
      System.err.println("[NeoRaven] Loading shader unavailable: " + e);
      return false;
    }
  }

  private static int uniform(String name) {
    return UNIFORMS.computeIfAbsent(name, n -> GL20.glGetUniformLocation(program, n));
  }

  private static void scalar(String name, float value) {
    GL20.glUniform1f(uniform(name), value);
  }

  private static int compile(int type, String source) {
    int shader = GL20.glCreateShader(type);
    GL20.glShaderSource(shader, source);
    GL20.glCompileShader(shader);
    if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
      String error = GL20.glGetShaderInfoLog(shader);
      GL20.glDeleteShader(shader);
      throw new IllegalStateException(error);
    }
    return shader;
  }

  private static void create() throws Exception {
    int vertex = 0, fragment = 0;
    try (var stream =
        NeoRavenLoadingShape.class.getResourceAsStream(
            "/assets/wurst/shaders/neoraven/m3_loading.frag")) {
      if (stream == null) throw new IllegalStateException("Missing loading shader");
      vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
      fragment =
          compile(
              GL20.GL_FRAGMENT_SHADER, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      program = GL20.glCreateProgram();
      GL20.glAttachShader(program, vertex);
      GL20.glAttachShader(program, fragment);
      GL20.glLinkProgram(program);
      if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
        throw new IllegalStateException(GL20.glGetProgramInfoLog(program));
      vao = GL30.glGenVertexArrays();
    } finally {
      if (vertex != 0) GL20.glDeleteShader(vertex);
      if (fragment != 0) GL20.glDeleteShader(fragment);
    }
  }

  public static void close() {
    if (program != 0) GL20.glDeleteProgram(program);
    if (vao != 0) GL30.glDeleteVertexArrays(vao);
    program = vao = 0;
    UNIFORMS.clear();
  }
}
