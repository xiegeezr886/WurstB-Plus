/*
 * Copyright (c) 2026 WurstB+ Plus contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package net.wurstclient.util.render;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import net.wurstclient.hud2.nanovg.GlStateGuard;
import org.lwjgl.opengl.*;

/** World-only LSD pass for versions without GameRenderer.loadEffect.
 * Copies the scene before drawing, so the shader never samples its output.
 * Owns one reusable color target, with no depth copy or per-frame allocation. */
public final class LsdPostEffect implements AutoCloseable {
  public static final LsdPostEffect INSTANCE = new LsdPostEffect();
  private static final String VERTEX = """
      #version 150
      out vec2 texCoord;
      void main() {
          vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
          texCoord = p;
          gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
      }
      """;
  private int program, vertices, framebuffer, texture, width, height, timeUniform;
  private long started;

  public void render(int scene, int w, int h) throws IOException {
    if (w <= 0 || h <= 0) return;
    try (var ignored = GlStateGuard.capture()) {
      GL13.glActiveTexture(GL13.GL_TEXTURE0);
      GL33.glBindSampler(0, 0);
      GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
      GL11.glDisable(GL11.GL_SCISSOR_TEST);
      GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
      ensure(w, h);

      GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, scene);
      GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
      GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h,
          GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

      GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scene);
      GL11.glViewport(0, 0, w, h);
      GL11.glDisable(GL11.GL_BLEND);
      GL11.glDisable(GL11.GL_DEPTH_TEST);
      GL11.glDepthMask(false);
      GL11.glDisable(GL11.GL_CULL_FACE);
      GL11.glDisable(GL11.GL_STENCIL_TEST);
      GL11.glColorMask(true, true, true, true);
      GL20.glUseProgram(program);
      GL30.glBindVertexArray(vertices);
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
      // Vanilla PostChain's Time cycles over twenty seconds. Preserve the
      // original shader, frequency and wobble amplitude rather than replacing it.
      float time = ((System.nanoTime() - started) % 20_000_000_000L)
          / 20_000_000_000F;
      GL20.glUniform1f(timeUniform, time);
      GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }
  }

  private void ensure(int w, int h) throws IOException {
    if (program == 0) create();
    if (width == w && height == h) return;
    GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8,
        w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer)null);
    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
    GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
        GL11.GL_TEXTURE_2D, texture, 0);
    if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER)
        != GL30.GL_FRAMEBUFFER_COMPLETE)
      throw new IOException("Incomplete LSD scene framebuffer");
    width = w;
    height = h;
  }

  private void create() throws IOException {
    String fragment;
    try (var stream = getClass().getResourceAsStream(
        "/assets/wurst/shaders/program/lsd_wobble.fsh")) {
      if (stream == null) throw new IOException("Missing LSD wobble shader");
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
      GL20.glUseProgram(program);
      GL20.glUniform1i(GL20.glGetUniformLocation(program, "DiffuseSampler"), 0);
      GL20.glUniform2f(GL20.glGetUniformLocation(program, "Frequency"), 512, 288);
      GL20.glUniform2f(GL20.glGetUniformLocation(program, "WobbleAmount"), .002F, .002F);
      timeUniform = GL20.glGetUniformLocation(program, "Time");
      vertices = GL30.glGenVertexArrays();
      framebuffer = GL30.glGenFramebuffers();
      texture = GL11.glGenTextures();
      GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
      GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
      started = System.nanoTime();
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

  @Override
  public void close() {
    if (texture != 0) GL11.glDeleteTextures(texture);
    if (framebuffer != 0) GL30.glDeleteFramebuffers(framebuffer);
    if (vertices != 0) GL30.glDeleteVertexArrays(vertices);
    if (program != 0) GL20.glDeleteProgram(program);
    program = vertices = framebuffer = texture = width = height = timeUniform = 0;
    started = 0;
  }
}
