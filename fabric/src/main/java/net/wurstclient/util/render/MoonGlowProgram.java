/* Moon FlowingGlow / flowing_glow.frag adaptation, Apache-2.0. */
package net.wurstclient.util.render;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.opengl.*;

public final class MoonGlowProgram implements AutoCloseable {
  public static final MoonGlowProgram INSTANCE = new MoonGlowProgram();
  private static final String VERTEX =
      """
      #version 150
      out vec2 uv;
      void main() {
          vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
          uv = p;
          gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
      }
      """;
  private int program, vertices;
  private final Map<String, Integer> uniforms = new HashMap<>();

  public void use(int mode) throws IOException {
    if (program == 0) create();
    GL20.glUseProgram(program);
    GL30.glBindVertexArray(vertices);
    GL11.glDisable(GL11.GL_DEPTH_TEST);
    GL11.glDepthMask(false);
    GL11.glDisable(GL11.GL_CULL_FACE);
    GL11.glDisable(GL11.GL_SCISSOR_TEST);
    GL11.glDisable(GL11.GL_STENCIL_TEST);
    GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
    GL11.glColorMask(true, true, true, true);
    GL13.glActiveTexture(GL13.GL_TEXTURE0);
    GL33.glBindSampler(0, 0);
    GL20.glUniform1i(uniform("mode"), mode);
    GL20.glUniform1i(uniform("glowTexture"), 0);
    GL20.glUniform1i(uniform("maskTexture"), 1);
  }

  public void scalar(String name, float value) {
    GL20.glUniform1f(uniform(name), value);
  }

  public void pair(String name, float x, float y) {
    GL20.glUniform2f(uniform(name), x, y);
  }

  public void draw() {
    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
  }

  private int uniform(String name) {
    return uniforms.computeIfAbsent(name, n -> GL20.glGetUniformLocation(program, n));
  }

  private void create() throws IOException {
    String fragment;
    try (var stream =
        getClass().getResourceAsStream("/assets/wurst/shaders/moon/flowing_glow.frag")) {
      if (stream == null) throw new IOException("Missing Moon flowing glow shader");
      fragment = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
    int vertex = 0, pixel = 0;
    try {
      vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX);
      pixel = compile(GL20.GL_FRAGMENT_SHADER, fragment);
      program = GL20.glCreateProgram();
      GL20.glAttachShader(program, vertex);
      GL20.glAttachShader(program, pixel);
      GL20.glLinkProgram(program);
      if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0)
        throw new IOException(GL20.glGetProgramInfoLog(program));
      vertices = GL30.glGenVertexArrays();
    } catch (IOException | RuntimeException e) {
      close();
      throw e;
    } finally {
      if (vertex != 0) GL20.glDeleteShader(vertex);
      if (pixel != 0) GL20.glDeleteShader(pixel);
    }
  }

  private static int compile(int type, String source) throws IOException {
    int shader = GL20.glCreateShader(type);
    GL20.glShaderSource(shader, source);
    GL20.glCompileShader(shader);
    if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
      String error = GL20.glGetShaderInfoLog(shader);
      GL20.glDeleteShader(shader);
      throw new IOException(error);
    }
    return shader;
  }

  public void close() {
    if (program != 0) GL20.glDeleteProgram(program);
    if (vertices != 0) GL30.glDeleteVertexArrays(vertices);
    program = vertices = 0;
    uniforms.clear();
  }
}
